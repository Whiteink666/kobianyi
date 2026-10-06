package com.sukisu.ultra.ui.util

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * 在修补 / 安装前判断用户选择的 boot 镜像是否为原厂镜像。
 *
 * 为什么需要它：ksud 在 boot_patch 里执行 `do_backup()` 时，只要带了 --backup
 * 就会无条件把传入的镜像存成"原厂备份"。如果用户选的其实已经是打过补丁的
 * 镜像，那份备份就被污染了 —— 之后用「恢复原厂镜像」救不回来。
 *
 * 检测方式：定位 boot 镜像的 ramdisk、解压，然后在 cpio 里查找
 * KernelSU / Magisk 注入的文件名（文件名在 cpio 里是明文）。
 */
object BootImageInspector {

    enum class Kind {
        /** 原厂镜像，未发现任何 root 补丁痕迹 */
        STOCK,

        /** 已经打过 KernelSU 补丁 */
        KERNELSU,

        /** 已经打过 Magisk 补丁 */
        MAGISK,

        /** 镜像结构正常，但 ramdisk 压缩格式不支持，无法判断 */
        UNKNOWN,

        /** 不是合法的 boot 镜像 */
        INVALID,
        ;

        /** 是否已经打过 root 补丁 */
        val isPatched: Boolean
            get() = this == KERNELSU || this == MAGISK
    }

    data class Result(
        val kind: Kind,
        /** ramdisk 压缩格式等细节，用于排查 */
        val detail: String = "",
    ) {
        val isPatched: Boolean
            get() = kind.isPatched
    }

    private const val HEAD_READ = 8192
    private const val MAX_RAMDISK = 96 * 1024 * 1024
    private const val BOOT_MAGIC = "ANDROID!"

    fun inspect(context: Context, uri: Uri): Result = try {
        context.contentResolver.openInputStream(uri)?.use { inspectStream(it) }
            ?: Result(Kind.INVALID)
    } catch (_: Exception) {
        Result(Kind.INVALID)
    }

    private fun inspectStream(stream: InputStream): Result {
        val head = ByteArray(HEAD_READ)
        var read = 0
        while (read < HEAD_READ) {
            val r = stream.read(head, read, HEAD_READ - read)
            if (r < 0) break
            read += r
        }
        if (read < 2048 || !hasBootMagic(head)) return Result(Kind.INVALID)

        val (ramdiskOffset, ramdiskSize) = locateRamdisk(head)
            ?: return Result(Kind.UNKNOWN, "header")

        val skipBytes = ramdiskOffset - read
        if (skipBytes > 0 && skipFully(stream, skipBytes) < skipBytes) {
            return Result(Kind.UNKNOWN, "truncated")
        }

        val wanted = ramdiskSize.coerceAtMost(MAX_RAMDISK)
        val raw = ByteArray(wanted)
        var off = 0
        while (off < wanted) {
            val r = stream.read(raw, off, wanted - off)
            if (r < 0) break
            off += r
        }
        val compressed = if (off == wanted) raw else raw.copyOf(off)

        val (plain, format) = decompress(compressed)
            ?: return Result(Kind.UNKNOWN, "compression")
        return Result(detectKind(plain), format)
    }

    private fun detectKind(cpio: ByteArray): Kind {
        // kernelsu.ko 是 KernelSU LKM 补丁塞进 ramdisk 的模块
        if (indexOf(cpio, "kernelsu.ko") >= 0) return Kind.KERNELSU
        if (indexOf(cpio, ".magisk") >= 0 || indexOf(cpio, "magiskinit") >= 0) return Kind.MAGISK
        // 两家的补丁都会把原 init 改名成 init.real
        if (indexOf(cpio, "init.real") >= 0) return Kind.KERNELSU
        return Kind.STOCK
    }

    // ---------- boot 头解析 ----------

    private fun hasBootMagic(head: ByteArray): Boolean =
        head.size >= BOOT_MAGIC.length &&
            String(head, 0, BOOT_MAGIC.length, Charsets.US_ASCII) == BOOT_MAGIC

    private fun u32(b: ByteArray, off: Int): Long {
        if (off + 4 > b.size) return 0
        return (b[off].toLong() and 0xFF) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 3].toLong() and 0xFF) shl 24)
    }

    private fun align(value: Long, alignment: Long): Long =
        (value + alignment - 1) / alignment * alignment

    /**
     * 返回 ramdisk 的 (文件内偏移, 大小)。
     * boot 头 v0-v2 与 v3/v4 的字段布局不同，分别处理。
     */
    private fun locateRamdisk(head: ByteArray): Pair<Long, Int>? {
        val kernelSize = u32(head, 8)
        val headerVersion = u32(head, 24)

        if (headerVersion >= 3 && headerVersion <= 4) {
            // v3/v4: header_size @20, ramdisk_size @12，页大小固定 4096
            val headerSize = u32(head, 20)
            val ramdiskSize = u32(head, 12)
            if (ramdiskSize <= 0) return null
            val offset = align(headerSize, 4096L) + align(kernelSize, 4096L)
            return offset to ramdiskSize.toInt()
        }

        // v0-v2: ramdisk_size @16, page_size @36，头占一页
        val rawPageSize = u32(head, 36)
        val pageSize = if (rawPageSize in VALID_PAGE_SIZES) rawPageSize else 4096L
        val ramdiskSize = u32(head, 16)
        if (ramdiskSize <= 0) return null
        val offset = pageSize + align(kernelSize, pageSize)
        return offset to ramdiskSize.toInt()
    }

    private val VALID_PAGE_SIZES = longArrayOf(2048, 4096, 8192, 16384, 32768, 65536, 131072)

    private fun skipFully(stream: InputStream, bytes: Long): Long {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (stream.read() < 0) break
                remaining--
            }
        }
        return bytes - remaining
    }

    // ---------- ramdisk 解压 ----------

    /** 返回 (解压后的 cpio 数据, 格式名) */
    private fun decompress(data: ByteArray): Pair<ByteArray, String>? {
        if (data.size < 6) return null

        // 未压缩的 cpio（newc 格式魔数）
        if (String(data, 0, 6, Charsets.US_ASCII) == "070701") {
            return data to "cpio"
        }

        // gzip
        if ((data[0].toInt() and 0xFF) == 0x1F && (data[1].toInt() and 0xFF) == 0x8B) {
            return try {
                GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() } to "gzip"
            } catch (_: Exception) {
                null
            }
        }

        // lz4 frame（AOSP mkbootimg 常用）
        if ((data[0].toInt() and 0xFF) == 0x04 && (data[1].toInt() and 0xFF) == 0x22 &&
            (data[2].toInt() and 0xFF) == 0x4D && (data[3].toInt() and 0xFF) == 0x18
        ) {
            return lz4Frame(data, 0)?.let { it to "lz4" }
        }

        // lz4 legacy
        if ((data[0].toInt() and 0xFF) == 0x02 && (data[1].toInt() and 0xFF) == 0x21 &&
            (data[2].toInt() and 0xFF) == 0x4C && (data[3].toInt() and 0xFF) == 0x18
        ) {
            return lz4Frame(data, 4)?.let { it to "lz4-legacy" }
        }

        return null
    }

    private fun readIntLE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    /** @param start lz4 frame 的 magic 起点；legacy 格式没有 frame 头，从块数据开始 */
    private fun lz4Frame(data: ByteArray, startMagic: Int): ByteArray? {
        var pos = startMagic
        var blockChecksum = false

        if (startMagic == 0) {
            if (pos + 7 > data.size) return null
            pos += 4 // magic
            val flg = data[pos++].toInt() and 0xFF
            pos++ // BD，块大小上限我们不需要
            if ((flg and 0x08) != 0) pos += 8 // content size
            if ((flg and 0x01) != 0) pos += 4 // dict id
            pos += 1 // header checksum
            blockChecksum = (flg and 0x10) != 0
        }

        val out = OutBuf()
        while (true) {
            if (pos + 4 > data.size) return null
            val blockHeader = readIntLE(data, pos)
            pos += 4
            if (blockHeader == 0) break // end mark
            val uncompressed = (blockHeader and 0x80000000.toInt()) != 0
            val size = blockHeader and 0x7FFFFFFF
            if (size <= 0 || pos + size > data.size) return null
            if (uncompressed) {
                out.write(data, pos, size)
            } else if (!lz4Block(data, pos, size, out)) {
                return null
            }
            pos += size
            if (blockChecksum) pos += 4
        }
        return out.toByteArray()
    }

    private fun lz4Block(src: ByteArray, start: Int, size: Int, out: OutBuf): Boolean {
        var i = start
        val end = start + size
        while (i < end) {
            val token = src[i++].toInt() and 0xFF

            var literalLen = token shr 4
            if (literalLen == 15) {
                while (true) {
                    if (i >= end) return false
                    val b = src[i++].toInt() and 0xFF
                    literalLen += b
                    if (b != 255) break
                }
            }
            if (i + literalLen > end) return false
            if (literalLen > 0) {
                out.write(src, i, literalLen)
                i += literalLen
            }
            // 最后一个序列可以只有 literals
            if (i >= end) break

            if (i + 2 > end) return false
            val distance = (src[i].toInt() and 0xFF) or ((src[i + 1].toInt() and 0xFF) shl 8)
            i += 2
            if (distance == 0 || distance > out.size) return false

            var matchLen = token and 0x0F
            if (matchLen == 15) {
                while (true) {
                    if (i >= end) return false
                    val b = src[i++].toInt() and 0xFF
                    matchLen += b
                    if (b != 255) break
                }
            }
            matchLen += 4 // MINMATCH
            out.copyMatch(distance, matchLen)
        }
        return true
    }

    /** 支持回看历史数据的输出缓冲，lz4 的 match 需要它 */
    private class OutBuf {
        private var buf = ByteArray(1 shl 20)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra <= buf.size) return
            var newSize = buf.size
            while (newSize < size + extra) newSize *= 2
            buf = buf.copyOf(newSize)
        }

        fun write(src: ByteArray, off: Int, len: Int) {
            ensure(len)
            System.arraycopy(src, off, buf, size, len)
            size += len
        }

        /** 从 distance 字节前开始复制 length 个字节（允许重叠，模拟 memmove） */
        fun copyMatch(distance: Int, length: Int) {
            ensure(length)
            var src = size - distance
            repeat(length) {
                buf[size++] = buf[src++]
            }
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    /** 朴素字节串搜索，cpio 里文件名是明文 */
    private fun indexOf(data: ByteArray, token: String): Int {
        val target = token.toByteArray(Charsets.US_ASCII)
        if (target.isEmpty() || data.size < target.size) return -1
        val limit = data.size - target.size
        for (i in 0..limit) {
            var j = 0
            while (j < target.size) {
                if (data[i + j] != target[j]) break
                j++
            }
            if (j == target.size) return i
        }
        return -1
    }
}
