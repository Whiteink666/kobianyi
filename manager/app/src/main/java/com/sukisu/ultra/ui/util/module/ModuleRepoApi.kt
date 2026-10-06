package com.sukisu.ultra.ui.util.module

import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.isNetworkAvailable
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class ModuleDetail(
    val readme: String,
    val readmeHtml: String,
    val latestTag: String,
    val latestTime: String,
    val latestAssetName: String?,
    val latestAssetUrl: String?,
    val releases: List<ReleaseInfo>,
    val homepageUrl: String,
    val sourceUrl: String,
    val url: String
)

data class ReleaseInfo(
    val name: String,
    val tagName: String,
    val publishedAt: String,
    val descriptionHTML: String,
    val assets: List<ReleaseAssetInfo>
)

data class ReleaseAssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val downloadCount: Int
)

// 模块仓库数据托管在我们自己的仓库里（官方 modules.kernelsu.org 已停止服务），
// 自己仓库里没有该模块时再回退到官方地址
private const val REPO_BASE =
    "https://raw.githubusercontent.com/Whiteink666/Whiteink-Manager/main"
private const val OFFICIAL_BASE = "https://modules.kernelsu.org"

/** 自己的仓库优先，取不到再回退官方 */
private fun fetchModuleJson(moduleId: String): JSONObject? {
    if (!isNetworkAvailable(ksuApp)) return null
    val path = "module/$moduleId.json"
    for (base in listOf(REPO_BASE, OFFICIAL_BASE)) {
        val obj = runCatching {
            ksuApp.okhttpClient.newCall(Request.Builder().url("$base/$path").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) null else JSONObject(resp.body.string())
                }
        }.getOrNull()
        if (obj != null) return obj
    }
    return null
}

fun sanitizeVersionString(version: String): String {
    return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
}

fun stripTicks(s: String): String {
    val t = s.trim()
    return if (t.startsWith("`") && t.endsWith("`") && t.length >= 2) t.substring(1, t.length - 1) else t
}

fun fetchReleaseDescriptionHtml(moduleId: String, latestTag: String): String? {
    val obj = fetchModuleJson(moduleId) ?: return null
    return runCatching {
        val releasesArray = obj.optJSONArray("releases") ?: return@runCatching null
        var fallbackHtml: String? = null
        for (i in 0 until releasesArray.length()) {
            val r = releasesArray.optJSONObject(i) ?: continue
            val descHtml = r.optString("descriptionHTML", "")
            if (fallbackHtml == null && descHtml.isNotBlank()) {
                fallbackHtml = descHtml
            }
            val rname = r.optString("name", r.optString("tagName", r.optString("version", "")))
            if (rname == latestTag && descHtml.isNotBlank()) {
                return@runCatching descHtml
            }
        }
        fallbackHtml
    }.getOrNull()
}


fun fetchModuleDetail(moduleId: String): ModuleDetail? {
    val obj = fetchModuleJson(moduleId) ?: return null
    return runCatching {
        val readme = obj.optString("readme", "")
            val readmeHtml = obj.optString("readmeHTML", "")
            val homepageUrl = stripTicks(obj.optString("homepageUrl", ""))
            val sourceUrl = stripTicks(obj.optString("sourceUrl", ""))
            val url = stripTicks(obj.optString("url", ""))
            val lr = obj.optJSONObject("latestRelease")
            var latestTag: String
            var latestTime = ""
            var latestAssetName: String? = null
            var latestAssetUrl: String? = null
            if (lr != null) {
                latestTag = lr.optString("name", lr.optString("version", ""))
                latestTime = lr.optString("time", "")
                var urlDl = lr.optString("downloadUrl", "")
                urlDl = stripTicks(urlDl)
                if (urlDl.isNotEmpty()) {
                    latestAssetName = urlDl.substringAfterLast('/')
                    latestAssetUrl = urlDl
                }
            } else {
                latestTag = obj.optString("latestRelease", "")
            }

            val releasesArray = obj.optJSONArray("releases")
            val releases = if (releasesArray != null) {
                (0 until releasesArray.length()).mapNotNull { rIdx ->
                    val r = releasesArray.optJSONObject(rIdx) ?: return@mapNotNull null
                    val rname = r.optString("name", r.optString("tagName", r.optString("version", "")))
                    val publishedAt = r.optString("publishedAt", "")
                    val descHtml = r.optString("descriptionHTML", "")
                    val assetsArray = r.optJSONArray("releaseAssets") ?: JSONArray()
                    val assets = (0 until assetsArray.length()).mapNotNull { aIdx ->
                        val a = assetsArray.optJSONObject(aIdx) ?: return@mapNotNull null
                        val aname = a.optString("name", "")
                        var adl = a.optString("downloadUrl", "")
                        adl = stripTicks(adl)
                        val asz = a.optLong("size", 0L)
                        val dcnt = a.optInt("downloadCount", 0)
                        if (aname.isEmpty() || adl.isEmpty()) null else ReleaseAssetInfo(aname, adl, asz, dcnt)
                    }
                    ReleaseInfo(
                        name = rname,
                        tagName = r.optString("tagName", rname),
                        publishedAt = publishedAt,
                        descriptionHTML = descHtml,
                        assets = assets
                    )
                }
            } else emptyList()

        ModuleDetail(
            readme = readme,
            readmeHtml = readmeHtml,
            latestTag = latestTag,
            latestTime = latestTime,
            latestAssetName = latestAssetName,
            latestAssetUrl = latestAssetUrl,
            releases = releases,
            homepageUrl = homepageUrl,
            sourceUrl = sourceUrl,
            url = url
        )
    }.getOrNull()
}
