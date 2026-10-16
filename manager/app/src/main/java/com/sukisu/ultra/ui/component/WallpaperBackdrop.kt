package com.sukisu.ultra.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukisu.ultra.data.repository.WallpaperRepository
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 把选中的壁纸铺在整个 App 的最底层。
 *
 * 没有选壁纸（或缓存丢了）时不做任何事，保持原来的纯色背景。
 * 图片上面盖一层半透明遮罩，否则文字在亮色壁纸上会看不清。
 */
@Composable
fun WallpaperBackdrop(
    dimAlpha: Float = 0.55f,
    content: @Composable () -> Unit,
) {
    val currentId by WallpaperRepository.currentId.collectAsStateWithLifecycle()
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val surface =
        if (isMiuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface

    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = currentId) {
        value = withContext(Dispatchers.IO) { WallpaperRepository.loadBitmap(currentId) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(surface.copy(alpha = dimAlpha))
            )
        }
        content()
    }
}
