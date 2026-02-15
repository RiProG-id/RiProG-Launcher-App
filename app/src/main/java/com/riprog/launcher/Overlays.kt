package com.riprog.launcher

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun DefaultLauncherPrompt(onLater: () -> Unit, onSetDefault: () -> Unit, settingsManager: SettingsManager) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.width(300.dp), shape = RoundedCornerShape(12.dp), color = Color.DarkGray) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.prompt_default_launcher_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text(stringResource(R.string.prompt_default_launcher_message), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onLater) { Text(stringResource(R.string.action_later), color = Color.White) }
                    TextButton(onClick = onSetDefault) { Text(stringResource(R.string.action_set_default), color = Color.Cyan) }
                }
            }
        }
    }
}

@Composable
fun FolderOverlay(folder: HomeItem, settingsManager: SettingsManager, model: LauncherModel?, allApps: List<AppItem>, onClose: () -> Unit, onAppClick: (HomeItem) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.width(300.dp).padding(16.dp), shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.8f)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(folder.folderName ?: "Folder", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                val items = folder.folderItems ?: emptyList()
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.heightIn(max = 300.dp)) {
                    items(items.size) { index -> AppIcon(items[index], model, allApps, settingsManager) }
                }
            }
        }
    }
}

@Composable
fun HomeMenu(
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val adaptiveColor = Color(ThemeUtils.getAdaptiveColor(context, settingsManager, true))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_home_menu), color = adaptiveColor, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                MenuItem(stringResource(R.string.menu_widgets), Icons.Default.Widgets, adaptiveColor, onWidgets)
                MenuItem(stringResource(R.string.menu_wallpaper), Icons.Default.Wallpaper, adaptiveColor, onWallpaper)
                MenuItem(stringResource(R.string.menu_settings), Icons.Default.Settings, adaptiveColor, onSettings)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = adaptiveColor) }
        },
        containerColor = if (settingsManager.isLiquidGlass()) Color.Black.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun MenuItem(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = color, fontSize = 16.sp)
    }
}

@Composable
fun TransformOverlayCompose(
    item: HomeItem,
    settingsManager: SettingsManager,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onMove: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var lastPageFlipTime by remember { mutableLongStateOf(0L) }

    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.2f))
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val cellWidthPx = maxWidthPx / GRID_COLUMNS
        val cellHeightPx = maxHeightPx / GRID_ROWS
        val edgeThresholdPx = with(density) { 60.dp.toPx() }

        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()

                item.col += dragAmount.x / cellWidthPx
                item.row += dragAmount.y / cellHeightPx

                val positionX = change.position.x
                val now = System.currentTimeMillis()
                if (now - lastPageFlipTime > 1500) {
                    if (positionX < edgeThresholdPx && pagerState.currentPage > 0) {
                        item.page -= 1
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        lastPageFlipTime = now
                    } else if (positionX > maxWidthPx - edgeThresholdPx && pagerState.currentPage < pagerState.pageCount - 1) {
                        item.page += 1
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        lastPageFlipTime = now
                    }
                }
                onMove()
            }
        })

        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(8.dp)) {
            Button(onClick = onRemove, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) { Text("Remove", color = Color.White) }
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) { Text("Cancel", color = Color.White) }
            Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) { Text("Save", color = Color.White) }
        }
    }
}
