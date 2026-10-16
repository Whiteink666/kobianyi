package com.sukisu.ultra.data.model

/**
 * 在线图库里的一张壁纸。
 *
 * 图库托管在 Whiteink-Manager 仓库的 wallpapers/ 目录，索引是 wallpapers/index.json：
 * {"version":1,"wallpapers":[{"id":"aurora","name":"极光","file":"aurora.jpg",
 *                             "author":"...","source":"..."}]}
 */
data class Wallpaper(
    val id: String,
    val name: String,
    val url: String,
    val author: String = "",
    val source: String = "",
)
