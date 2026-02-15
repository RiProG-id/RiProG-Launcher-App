package com.riprog.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.*
import kotlin.math.roundToInt

@Composable
fun HomeItemComponent(
    item: HomeItem,
    allApps: List<AppItem>,
    cellWidth: Int,
    cellHeight: Int,
    settingsManager: SettingsManager,
    model: LauncherModel?,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val density = LocalDensity.current
    val x = item.col * cellWidth
    val y = item.row * cellHeight
    val width = (item.spanX * cellWidth).toInt()
    val height = (item.spanY * cellHeight).toInt()
    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .graphicsLayer {
                rotationZ = item.rotation
                scaleX = item.scaleX
                scaleY = item.scaleY
                rotationX = item.tiltX
                rotationY = item.tiltY
            }
            .pointerInput(item) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
    ) {
        when (item.type) {
            HomeItem.Type.APP -> AppIcon(item, model, allApps, settingsManager)
            HomeItem.Type.WIDGET -> WidgetView(item, appWidgetManager, appWidgetHost)
            HomeItem.Type.FOLDER -> FolderPreview(item, model, allApps, settingsManager)
            HomeItem.Type.CLOCK -> ClockView(settingsManager)
            else -> {}
        }
    }
}

@Composable
fun AppIcon(item: HomeItem, model: LauncherModel?, allApps: List<AppItem>, settingsManager: SettingsManager) {
    val iconScale = settingsManager.iconScale
    var iconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var label by remember { mutableStateOf("...") }
    LaunchedEffect(item, allApps) {
        val app = allApps.find { it.packageName == item.packageName }
        if (app != null) {
            label = app.label
            model?.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
                override fun onIconLoaded(icon: android.graphics.Bitmap?) { iconBitmap = icon }
            })
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(48.dp * iconScale)) {
            if (iconBitmap != null) {
                androidx.compose.foundation.Image(iconBitmap!!.asImageBitmap(), null, Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f), CircleShape))
            }
        }
        if (!settingsManager.isHideLabels()) {
            Text(label, fontSize = (10 * iconScale).sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun WidgetView(item: HomeItem, appWidgetManager: AppWidgetManager?, appWidgetHost: AppWidgetHost?) {
    if (appWidgetManager == null || appWidgetHost == null) return
    val info = appWidgetManager.getAppWidgetInfo(item.widgetId) ?: return
    AndroidView(
        factory = { context ->
            appWidgetHost.createView(context, item.widgetId, info).apply {
                setAppWidget(item.widgetId, info)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun FolderPreview(item: HomeItem, model: LauncherModel?, allApps: List<AppItem>, settingsManager: SettingsManager) {
    val iconScale = settingsManager.iconScale
    val folderItems = item.folderItems ?: emptyList()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier.size(48.dp * iconScale).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(4.dp)
        ) {
            LazyVerticalGrid(columns = GridCells.Fixed(2), userScrollEnabled = false) {
                items(minOf(4, folderItems.size)) { index ->
                    val subItem = folderItems[index]
                    var subBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(subItem) {
                        val app = allApps.find { it.packageName == subItem.packageName }
                        if (app != null) {
                            model?.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
                                override fun onIconLoaded(icon: android.graphics.Bitmap?) { subBitmap = icon }
                            })
                        }
                    }
                    if (subBitmap != null) {
                        androidx.compose.foundation.Image(subBitmap!!.asImageBitmap(), null, Modifier.size(20.dp))
                    }
                }
            }
        }
        if (!settingsManager.isHideLabels()) {
            Text(item.folderName ?: "", fontSize = (10 * iconScale).sp, color = Color.White)
        }
    }
}

@Composable
fun ClockView(settingsManager: SettingsManager) {
    val context = LocalContext.current
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            time = DateFormat.getTimeFormat(context).format(cal.time)
            date = DateFormat.getMediumDateFormat(context).format(cal.time)
            delay(1000)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
        Text(time, fontSize = 64.sp, color = Color.White, fontWeight = FontWeight.Light)
        Text(date, fontSize = 18.sp, color = Color.White.copy(alpha = 0.7f))
    }
}
