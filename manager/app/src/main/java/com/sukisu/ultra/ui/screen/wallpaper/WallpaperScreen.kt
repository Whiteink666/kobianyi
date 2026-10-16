package com.sukisu.ultra.ui.screen.wallpaper

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.Wallpaper
import com.sukisu.ultra.data.repository.WallpaperRepository
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 在线壁纸图库。图片列表来自 Whiteink-Manager 仓库的 wallpapers/index.json，
 * 选中后下载到本机缓存并立即应用到整个 App 背景。
 */
@Composable
fun WallpaperScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMiuix = LocalUiMode.current == UiMode.Miuix

    val backgroundColor =
        if (isMiuix) MiuixTheme.colorScheme.background else MaterialTheme.colorScheme.background
    val contentColor =
        if (isMiuix) MiuixTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground
    val surfaceColor =
        if (isMiuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
    // surface 在壁纸模式下是透明的，瓦片底色要用不透明的容器色
    val tileColor =
        if (isMiuix) MiuixTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val primaryColor =
        if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary

    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var wallpapers by remember { mutableStateOf<List<Wallpaper>>(emptyList()) }
    var applyingId by remember { mutableStateOf<String?>(null) }
    val currentId by WallpaperRepository.currentId.collectAsStateWithLifecycle()

    fun refresh() {
        scope.launch {
            loading = true
            failed = false
            WallpaperRepository.fetchList()
                .onSuccess { list ->
                    wallpapers = list
                    WallpaperRepository.clearUnused(list.map { it.id }.toSet())
                }
                .onFailure { failed = true }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navigator.pop() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = contentColor)
            }
            Text(
                text = stringResource(id = R.string.wallpaper_title),
                color = contentColor,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.wallpaper_refresh), tint = contentColor)
            }
        }

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryColor)
                }
            }
            failed -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(id = R.string.wallpaper_load_failed), color = contentColor)
                }
            }
            wallpapers.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(id = R.string.wallpaper_empty), color = contentColor)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        WallpaperTile(
                            title = stringResource(id = R.string.wallpaper_default),
                            subtitle = null,
                            bitmap = null,
                            selected = currentId.isBlank(),
                            busy = false,
                            surfaceColor = tileColor,
                            contentColor = contentColor,
                            primaryColor = primaryColor,
                            onClick = { WallpaperRepository.select("") }
                        )
                    }
                    items(wallpapers, key = { it.id }) { wallpaper ->
                        WallpaperTile(
                            title = wallpaper.name,
                            subtitle = wallpaper.author.ifBlank { null },
                            bitmap = rememberWallpaperPreview(wallpaper),
                            selected = currentId == wallpaper.id,
                            busy = applyingId == wallpaper.id,
                            surfaceColor = tileColor,
                            contentColor = contentColor,
                            primaryColor = primaryColor,
                            onClick = {
                                scope.launch {
                                    applyingId = wallpaper.id
                                    val ok = withContext(Dispatchers.IO) {
                                        if (WallpaperRepository.fileFor(wallpaper.id).exists()) {
                                            true
                                        } else {
                                            WallpaperRepository.download(wallpaper).isSuccess
                                        }
                                    }
                                    applyingId = null
                                    if (ok) {
                                        WallpaperRepository.select(wallpaper.id)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.wallpaper_applied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.wallpaper_download_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 优先用本地缓存，没有再下载，避免每次进入列表都重新拉图。 */
@Composable
private fun rememberWallpaperPreview(wallpaper: Wallpaper): Bitmap? {
    var bitmap by remember(wallpaper.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wallpaper.id) {
        withContext(Dispatchers.IO) {
            if (!WallpaperRepository.fileFor(wallpaper.id).exists()) {
                WallpaperRepository.download(wallpaper)
            }
            bitmap = WallpaperRepository.loadBitmap(wallpaper.id, maxWidth = 480)
        }
    }
    return bitmap
}

@Composable
private fun WallpaperTile(
    title: String,
    subtitle: String?,
    bitmap: Bitmap?,
    selected: Boolean,
    busy: Boolean,
    surfaceColor: Color,
    contentColor: Color,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primaryColor.copy(alpha = 0.18f) else surfaceColor)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (busy) {
                CircularProgressIndicator(
                    color = primaryColor,
                    modifier = Modifier
                        .width(28.dp)
                        .height(28.dp)
                )
            }
        }
        Text(
            text = title,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = contentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
