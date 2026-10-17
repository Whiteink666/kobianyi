package com.sukisu.ultra.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sukisu.ultra.data.model.Wallpaper
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * 在线壁纸图库。
 *
 * 图片托管在 Whiteink-Manager 仓库（和模块源、更新源同一个仓库），
 * Manager 只拉索引再按需下载，选中的图片缓存在 filesDir/wallpapers 下。
 */
object WallpaperRepository {

    private const val TAG = "WallpaperRepository"
    private const val PREFS = "settings"
    private const val KEY_CURRENT = "wallpaper_id"

    // 图库源，按顺序回退：
    // 1) raw 是实时的，改完索引立刻生效，但国内（尤其手机直连）常连不上
    // 2) gh-proxy 是实时代理，国内可用，用来兜底
    // 3) jsDelivr 的 CDN 国内稳定，但缓存约 12 小时，加了新图要等半天，放最后
    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/Whiteink666/Whiteink-Manager/main/wallpapers/",
        "https://gh-proxy.com/https://raw.githubusercontent.com/Whiteink666/Whiteink-Manager/main/wallpapers/",
        "https://cdn.jsdelivr.net/gh/Whiteink666/Whiteink-Manager@main/wallpapers/",
    )

    private val prefs by lazy {
        ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 当前选中的壁纸 id，空串表示用默认背景。选中后背景会立即刷新。 */
    private val _currentId = MutableStateFlow(prefs.getString(KEY_CURRENT, "").orEmpty())
    val currentId: StateFlow<String> = _currentId.asStateFlow()

    private fun dir(): File =
        File(ksuApp.filesDir, "wallpapers").apply { if (!exists()) mkdirs() }

    fun fileFor(id: String): File = File(dir(), "$id.wallpaper")

    fun select(id: String) {
        prefs.edit().putString(KEY_CURRENT, id).apply()
        _currentId.value = id
    }

    /** 卡片底色不透明度：壁纸模式下卡片太实会挡住背景，让用户自己调。 */
    private const val KEY_CARD_ALPHA = "wallpaper_card_alpha"
    const val CARD_ALPHA_MIN = 0.3f
    const val CARD_ALPHA_MAX = 1f
    const val CARD_ALPHA_DEFAULT = 0.75f

    private val _cardAlpha = MutableStateFlow(
        prefs.getFloat(KEY_CARD_ALPHA, CARD_ALPHA_DEFAULT).coerceIn(CARD_ALPHA_MIN, CARD_ALPHA_MAX)
    )
    val cardAlpha: StateFlow<Float> = _cardAlpha.asStateFlow()

    fun setCardAlpha(value: Float) {
        val clamped = value.coerceIn(CARD_ALPHA_MIN, CARD_ALPHA_MAX)
        prefs.edit().putFloat(KEY_CARD_ALPHA, clamped).apply()
        _cardAlpha.value = clamped
    }

    /** 拉取图库索引。 */
    suspend fun fetchList(): Result<List<Wallpaper>> = withContext(Dispatchers.IO) {
        runCatching {
            var lastError: Throwable? = null
            for (base in SOURCES) {
                try {
                    return@runCatching fetchFrom(base)
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "wallpaper source unavailable: $base", e)
                }
            }
            throw lastError ?: IllegalStateException("no wallpaper source")
        }.onFailure { Log.w(TAG, "fetch wallpaper list failed", it) }
    }

    /** 从一个源拉索引；图片地址也用同一个源拼，避免两个源混用。 */
    private fun fetchFrom(base: String): List<Wallpaper> {
        ksuApp.okhttpClient.newCall(Request.Builder().url(base + "index.json").build())
            .execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val arr = JSONObject(resp.body.string()).optJSONArray("wallpapers")
                    ?: return emptyList()
                return (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id").trim()
                    val file = o.optString("file").trim()
                    // id 会当作本地缓存文件名，挡掉路径穿越
                    if (id.isEmpty() || file.isEmpty()) return@mapNotNull null
                    if (id.any { it == '/' || it == '\\' || it == '.' }) return@mapNotNull null
                    Wallpaper(
                        id = id,
                        name = o.optString("name").ifBlank { id },
                        url = if (file.startsWith("http")) file else base + file,
                        author = o.optString("author", ""),
                        source = o.optString("source", ""),
                    )
                }
            }
    }

    /** 下载一张壁纸到本地缓存。 */
    suspend fun download(wallpaper: Wallpaper): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = fileFor(wallpaper.id)
            val tmp = File(target.parentFile, "${target.name}.tmp")
            try {
                ksuApp.okhttpClient.newCall(Request.Builder().url(wallpaper.url).build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body.byteStream().use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                if (tmp.length() == 0L) error("empty download")
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) error("cannot move to cache")
                target
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }.onFailure { Log.w(TAG, "download ${wallpaper.id} failed", it) }
    }

    /**
     * 按采样率解码已缓存的壁纸，避免整张原图吃满内存。
     * 返回 null 表示没有可用缓存（未下载或解码失败），调用方应回退到默认背景。
     */
    fun loadBitmap(id: String, maxWidth: Int = 720): Bitmap? {
        if (id.isBlank()) return null
        val file = fileFor(id)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / sample > maxWidth) sample *= 2
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }.onFailure { Log.w(TAG, "decode wallpaper $id failed", it) }.getOrNull()
    }

    /** 清掉索引里已经不存在的图片，避免缓存无限增长。 */
    fun clearUnused(validIds: Set<String>) {
        runCatching {
            dir().listFiles()?.forEach { f ->
                val id = f.name.removeSuffix(".wallpaper")
                if (id !in validIds) f.delete()
            }
        }.onFailure { Log.w(TAG, "cleanup failed", it) }
    }
}
