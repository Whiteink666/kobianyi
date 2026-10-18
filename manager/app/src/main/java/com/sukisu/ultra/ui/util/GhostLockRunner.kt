package com.sukisu.ultra.ui.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.ghostlock.data.AndroidProfileConfigController
import com.sukisu.ultra.ui.util.ghostlock.data.AssetConfigLoader
import com.sukisu.ultra.ui.util.ghostlock.data.BuiltinProfileCatalog
import com.sukisu.ultra.ui.util.ghostlock.data.UserProfileStore
import com.sukisu.ultra.ui.util.ghostlock.domain.model.CpuPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * GhostLock (CVE-2026-43499, futex PI UAF): a temporary, non-persistent root that
 * needs no unlocked bootloader. Everything runs in userspace: the exploit lifts
 * SELinux, swaps creds with init_cred, clears seccomp and then hands off to a
 * late-loaded ksud, exactly like DirtyFrag Root does.
 *
 * Ported from YuKongA/ghostlock-app (exploit + kernel profiles) and
 * p2p3p/GhostLock-for-OnePlus (the original CVE-2026-43499 write-up). Unlike the
 * upstream app we always run inside our own process (no Shizuku) and our own
 * libksud.so is staged for the handoff, so the root session belongs to this app.
 */
object GhostLockRunner {

    private const val TAG = "GhostLock"

    private const val EXPLOIT_BIN = "libghostlock.so"
    private const val KSUD_LIB = "libksud.so"
    private const val EXTRACT_BIN = "libextract.so"

    private const val PROFILE_DIR = "kernel_profiles"
    private const val USER_PROFILE_DIR = "ghostlock_profiles"

    /** Status events reported by the native process on stdout, ACKed on stdin. */
    private const val STATUS_MARKER = "GLK_STATUS"
    private const val STATUS_DISABLED = "GLK_STATUS_DISABLED"
    private const val STATUS_ACK = "GLK_STATUS_ACK\n"

    private const val RUN_TIMEOUT_SECONDS = 300L

    const val RC_OK = 0
    const val RC_FAILED = 1
    /** 内置 profile 里没有本机内核（os.version 没匹配上） */
    const val RC_NO_PROFILE = 2

    fun isArchSupported(): Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            isArchSupported() &&
            File(ksuApp.applicationInfo.nativeLibraryDir, EXPLOIT_BIN).isFile

    /** 本机内核是否有内置 profile（没有就跑不了，入口应该直接说明） */
    fun hasProfile(): Boolean = kernelRelease() in BuiltinProfileCatalog(ksuApp).unames

    fun kernelRelease(): String = System.getProperty("os.version").orEmpty()

    /** 镜像解析用的 extractor 是否打进了 APK（CI 编的 Rust 二进制） */
    fun isExtractorAvailable(): Boolean =
        File(ksuApp.applicationInfo.nativeLibraryDir, EXTRACT_BIN).isFile

    /** 是否已经导入过一份自己解析出来的 profile */
    fun hasUserProfile(): Boolean = controller.activeUserProfile() != null

    private val userProfiles by lazy {
        UserProfileStore(
            directory = File(ksuApp.filesDir, USER_PROFILE_DIR),
            assetLoader = AssetConfigLoader(ksuApp),
        )
    }

    private val controller: AndroidProfileConfigController by lazy {
        AndroidProfileConfigController(
            context = ksuApp,
            filesDir = ksuApp.filesDir,
            userProfiles = userProfiles,
            preferences = ksuApp.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE),
        )
    }

    /**
     * 从内核镜像里把 offset 算出来：boot.img / payload.bin / 裸 Image 都行。
     * 生成的 profile 存进用户 profile 目录并立即选中，之后 [run] 会直接用它。
     */
    suspend fun parseProfile(input: File, onLog: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (!isExtractorAvailable()) {
                onLog("extractor missing: ${EXTRACT_BIN}\n")
                return@withContext false
            }
            val out = File(ksuApp.filesDir, "ghostlock_parse.conf")
            out.delete()
            val release = kernelRelease()
            val binary = File(ksuApp.applicationInfo.nativeLibraryDir, EXTRACT_BIN)
            onLog("* parsing: ${input.name}\n")
            val process = ProcessBuilder(
                binary.absolutePath,
                input.absolutePath,
                "--format", "conf",
                "--out", out.absolutePath,
                "--work-dir", ksuApp.filesDir.absolutePath,
            )
                .directory(ksuApp.filesDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["TMPDIR"] = ksuApp.filesDir.absolutePath
                    environment()["HOME"] = ksuApp.filesDir.absolutePath
                }
                .start()
            var rc = 1
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        Log.i(TAG, line)
                        onLog("$line\n")
                    }
                }
                rc = if (process.waitFor(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.exitValue()
                } else {
                    onLog("\n* parse timed out\n")
                    1
                }
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
            if (rc != 0 || !out.isFile) {
                onLog("\n* parse failed (exit=$rc)\n")
                return@withContext false
            }

            val document = out.readText()
            val name = "${release.ifBlank { "parsed" }}.conf"
            userProfiles.save(name, document)
            controller.selectUserProfile(name, release, recommendedCpuPair())
            val config = controller.load(release, recommendedCpuPair())
            onLog("\n* profile saved: $name (hasProfile=${config.hasProfile})\n")
            config.hasProfile
        }

    suspend fun run(onLog: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            onLog("unsupported: needs Android 13+ on arm64 with ${EXPLOIT_BIN}\n")
            return@withContext RC_FAILED
        }

        reportDeviceInfo(onLog)

        val workDir = ksuApp.filesDir
        val release = kernelRelease()
        val pair = recommendedCpuPair()

        stage(
            File(ksuApp.applicationInfo.nativeLibraryDir, KSUD_LIB),
            File(workDir, "ksud"),
        )?.let { error ->
            onLog("staging ksud failed: $error\n")
            return@withContext RC_FAILED
        }
        onLog("* ksud: ${File(workDir, "ksud").absolutePath}\n")

        val blob = try {
            resolveProfile(release, pair, onLog)
        } catch (e: Exception) {
            Log.e(TAG, "profile resolution failed", e)
            onLog("profile failed: ${e.message}\n")
            null
        }
        if (blob == null) {
            onLog("\n* no profile for kernel $release\n")
            return@withContext RC_NO_PROFILE
        }
        onLog("* profile: ${blob.size} bytes\n\n")

        val process = startProcess(workDir)
        var rc = RC_FAILED
        try {
            // 4 字节大端长度 + GLK1 文档，之后 stdin 保持打开用作状态 ACK 通道
            process.outputStream.write(framed(blob))
            process.outputStream.flush()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    Log.i(TAG, line)
                    if (line.startsWith(STATUS_MARKER)) {
                        if (!line.contains(STATUS_DISABLED)) ack(process)
                        continue
                    }
                    onLog("$line\n")
                }
            }
            rc = if (process.waitFor(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.exitValue()
            } else {
                onLog("\n* timed out after ${RUN_TIMEOUT_SECONDS}s\n")
                RC_FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "ghostlock failed", e)
            onLog("error: ${e.message}\n")
        } finally {
            runCatching { process.outputStream.close() }
            if (process.isAlive) process.destroyForcibly()
        }
        if (rc == RC_OK) RC_OK else RC_FAILED
    }

    private fun startProcess(workDir: File): Process {
        val binary = File(ksuApp.applicationInfo.nativeLibraryDir, EXPLOIT_BIN)
        val ksuLog = File(workDir, "ghostlock-ksu-${System.currentTimeMillis()}.log")
        return ProcessBuilder(
            binary.absolutePath,
            "--ghostlock-app-call",
            "--enable-status-record",
        )
            .directory(workDir)
            .redirectErrorStream(true)
            .apply {
                environment()["GHOSTLOCK_HOME"] = workDir.absolutePath
                environment()["TMPDIR"] = workDir.absolutePath
                environment()["HOME"] = workDir.absolutePath
                environment()["GHOSTLOCK_KSU_LOG"] = ksuLog.absolutePath
            }
            .start()
    }

    /** 解析内置内核 profile 并编码成 native 的 GLK1 文档 */
    private suspend fun resolveProfile(
        release: String,
        pair: CpuPair,
        onLog: (String) -> Unit,
    ): ByteArray? {
        // 内置 profile 和解析出来的用户 profile 由同一个 controller 合并
        val config = controller.load(release, pair)
        onLog("* cpu pair: $pair\n")
        if (!config.hasProfile) return null
        return controller.nativeDocument(config)
    }

    private fun reportDeviceInfo(onLog: (String) -> Unit) {
        onLog("* manager: ${BuildConfig.VERSION_NAME} (${ksuApp.packageName})\n")
        onLog("* manufacturer: ${Build.MANUFACTURER}\n")
        onLog("* model: ${Build.MODEL}\n")
        onLog("* android: ${Build.VERSION.RELEASE}\n")
        onLog("* security patch: ${Build.VERSION.SECURITY_PATCH}\n")
        onLog("* kernel: ${kernelRelease()}\n")
    }

    /**
     * 和上游一致：按 cpufreq 分组取第一个簇的一对 CPU（通常是大核）。
     * 读不到 sysfs 就用 0,1。
     */
    private fun recommendedCpuPair(): CpuPair {
        val online = readSysFile("/sys/devices/system/cpu/online")
            .split(',')
            .flatMap { part ->
                val range = part.trim().split('-').mapNotNull { it.toIntOrNull() }
                when (range.size) {
                    1 -> range
                    2 -> (range[0]..range[1]).toList()
                    else -> emptyList()
                }
            }
        val clusters = online.groupBy { maxFreq(it) }
            .filterKeys { it > 0 }
            .toSortedMap(compareByDescending { it })
        val pair = clusters.values.firstOrNull()
            ?.sorted()
            ?.chunked(2)
            ?.firstOrNull { it.size == 2 }
        return if (pair != null) CpuPair(pair[0], pair[1]) else CpuPair(0, 1)
    }

    private fun maxFreq(cpu: Int): Long =
        readSysFile("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").toLongOrNull() ?: -1L

    private fun readSysFile(path: String): String =
        File(path).takeIf { it.isFile }?.useLines { it.firstOrNull()?.trim().orEmpty() }
            ?: ""

    private fun framed(blob: ByteArray): ByteArray {
        val size = blob.size
        return byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        ) + blob
    }

    private fun ack(process: Process) {
        runCatching {
            process.outputStream.write(STATUS_ACK.toByteArray(StandardCharsets.UTF_8))
            process.outputStream.flush()
        }
    }

    private fun stage(src: File, dest: File): String? {
        if (!src.isFile) return "${src.name} not found"
        return try {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            if (!dest.setExecutable(true, false)) "setExecutable failed: ${dest.absolutePath}"
            else null
        } catch (e: Exception) {
            e.message
        }
    }

    /** 内置 profile 目录，供 UI 展示用 */
    fun profileDir(): String = PROFILE_DIR
}
