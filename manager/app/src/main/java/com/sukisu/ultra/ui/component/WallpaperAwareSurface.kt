package com.sukisu.ultra.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukisu.ultra.data.repository.WallpaperRepository
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 只在主屏子树内把页面底色透明化，让全局 WallpaperBackdrop 的壁纸透出来。
 *
 * 为什么不改全局主题：导航栈会同时合成多层页面，二级页若也透明，栈底
 * 页面的内容就会从上层透出来（页面穿透）。二级页保持实底正好盖住下层，
 * 代价是二级页看不到壁纸。
 *
 * Miuix 的卡片用 surfaceContainer、Material 用 surfaceContainerLow 系列，
 * 都不在透明名单里，所以卡片内容仍然可读。
 */
@Composable
fun WallpaperAwareSurface(content: @Composable () -> Unit) {
    val wallpaperId by WallpaperRepository.currentId.collectAsStateWithLifecycle()
    val cardAlpha by WallpaperRepository.cardAlpha.collectAsStateWithLifecycle()
    if (wallpaperId.isBlank()) {
        content()
        return
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val base = MiuixTheme.colorScheme
            MiuixTheme(
                colors = base.copy(
                    background = Color.Transparent,
                    surface = Color.Transparent,
                    surfaceContainer = base.surfaceContainer.copy(alpha = cardAlpha),
                )
            ) { content() }
        }

        UiMode.Material -> {
            val base = MaterialTheme.colorScheme
            MaterialTheme(
                colorScheme = base.copy(
                    background = Color.Transparent,
                    surfaceContainer = base.surfaceContainer.copy(alpha = cardAlpha),
                    surfaceContainerLow = base.surfaceContainerLow.copy(alpha = cardAlpha),
                    surfaceVariant = base.surfaceVariant.copy(alpha = cardAlpha),
                ),
                // 保留外层的形状和字体，只换配色
                shapes = MaterialTheme.shapes,
                typography = MaterialTheme.typography,
                content = content
            )
        }
    }
}
