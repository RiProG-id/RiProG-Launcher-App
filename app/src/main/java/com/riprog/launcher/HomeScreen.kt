package com.riprog.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

const val GRID_COLUMNS = 4
const val GRID_ROWS = 6

@Composable
fun MainScreen(
    settingsManager: SettingsManager,
    model: LauncherModel?,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?
) {
    val context = LocalContext.current
    var homeItems by remember { mutableStateOf(settingsManager.getHomeItems().toMutableList()) }
    var allApps by remember { mutableStateOf(emptyList<AppItem>()) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    var currentFolder by remember { mutableStateOf<HomeItem?>(null) }
    var transformingItem by remember { mutableStateOf<HomeItem?>(null) }
    var showDefaultPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        model?.loadApps(object : LauncherModel.OnAppsLoadedListener {
            override fun onAppsLoaded(apps: List<AppItem>) { allApps = apps }
        })
        if (!isDefaultLauncher(context)) {
            val lastShown = settingsManager.lastDefaultPromptTimestamp
            val count = settingsManager.defaultPromptCount
            if (System.currentTimeMillis() - lastShown > 24 * 60 * 60 * 1000 && count < 5) {
                showDefaultPrompt = true
            }
        }
    }

    BackHandler(enabled = isDrawerOpen || currentFolder != null || transformingItem != null) {
        if (isDrawerOpen) isDrawerOpen = false
        else if (currentFolder != null) currentFolder = null
        else if (transformingItem != null) transformingItem = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            settingsManager = settingsManager,
            model = model,
            homeItems = homeItems,
            allApps = allApps,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager,
            onItemClick = { item ->
                when (item.type) {
                    HomeItem.Type.APP -> {
                        val intent = context.packageManager.getLaunchIntentForPackage(item.packageName ?: "")
                        if (intent != null) context.startActivity(intent)
                    }
                    HomeItem.Type.FOLDER -> currentFolder = item
                    HomeItem.Type.WIDGET -> transformingItem = item
                    else -> {}
                }
            },
            onItemLongClick = { item ->
                if (settingsManager.isFreeformHome() || item.type == HomeItem.Type.WIDGET) {
                    transformingItem = item
                }
            },
            onPageChanged = { }
        )

        if (isDrawerOpen) {
            AppDrawer(
                apps = allApps,
                settingsManager = settingsManager,
                model = model,
                onAppClick = { app ->
                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) context.startActivity(intent)
                    isDrawerOpen = false
                },
                onAppLongClick = { app ->
                    isDrawerOpen = false
                }
            )
        }

        if (currentFolder != null) {
            FolderOverlay(
                folder = currentFolder!!,
                settingsManager = settingsManager,
                model = model,
                allApps = allApps,
                onClose = { currentFolder = null },
                onAppClick = { appItem ->
                    val intent = context.packageManager.getLaunchIntentForPackage(appItem.packageName ?: "")
                    if (intent != null) context.startActivity(intent)
                    currentFolder = null
                }
            )
        }

        if (transformingItem != null) {
            TransformOverlayCompose(
                item = transformingItem!!,
                settingsManager = settingsManager,
                onSave = {
                    settingsManager.saveHomeItems(homeItems, settingsManager.pageCount)
                    transformingItem = null
                },
                onRemove = {
                    homeItems.remove(transformingItem)
                    settingsManager.saveHomeItems(homeItems, settingsManager.pageCount)
                    transformingItem = null
                },
                onCancel = { transformingItem = null }
            )
        }

        if (showDefaultPrompt) {
            DefaultLauncherPrompt(
                onLater = {
                    showDefaultPrompt = false
                    settingsManager.lastDefaultPromptTimestamp = System.currentTimeMillis()
                    settingsManager.incrementDefaultPromptCount()
                },
                onSetDefault = {
                    showDefaultPrompt = false
                    context.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
                },
                settingsManager = settingsManager
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        if (dragAmount.y < -50) isDrawerOpen = true
                    }
                }
        )
    }
}

fun isDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
    val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

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
fun HomeScreen(
    settingsManager: SettingsManager,
    model: LauncherModel?,
    homeItems: List<HomeItem>,
    allApps: List<AppItem>,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onItemClick: (HomeItem) -> Unit,
    onItemLongClick: (HomeItem) -> Unit,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = settingsManager.pageCount
    val pagerState = rememberPagerState(pageCount = { pageCount })
    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            HomeGrid(
                pageIndex = pageIndex,
                homeItems = homeItems.filter { it.page == pageIndex },
                allApps = allApps,
                settingsManager = settingsManager,
                model = model,
                appWidgetHost = appWidgetHost,
                appWidgetManager = appWidgetManager,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick
            )
        }
        PageIndicator(
            pageCount = pageCount,
            currentPage = pagerState.currentPage,
            accentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
        )
    }
}

@Composable
fun HomeGrid(
    pageIndex: Int,
    homeItems: List<HomeItem>,
    allApps: List<AppItem>,
    settingsManager: SettingsManager,
    model: LauncherModel?,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onItemClick: (HomeItem) -> Unit,
    onItemLongClick: (HomeItem) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellWidth = constraints.maxWidth / GRID_COLUMNS
        val cellHeight = constraints.maxHeight / GRID_ROWS
        homeItems.forEach { item ->
            key(item) {
                HomeItemComponent(
                    item = item,
                    allApps = allApps,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    settingsManager = settingsManager,
                    model = model,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) }
                )
            }
        }
    }
}

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
fun TransformOverlayCompose(item: HomeItem, settingsManager: SettingsManager, onSave: () -> Unit, onRemove: () -> Unit, onCancel: () -> Unit) {
    var rotation by remember { mutableFloatStateOf(item.rotation) }
    var scaleX by remember { mutableFloatStateOf(item.scaleX) }
    var scaleY by remember { mutableFloatStateOf(item.scaleY) }
    var col by remember { mutableFloatStateOf(item.col) }
    var row by remember { mutableFloatStateOf(item.row) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)).pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            col += dragAmount.x / 100f
            row += dragAmount.y / 100f
            item.col = col
            item.row = row
        }
    }) {
        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(8.dp)) {
            Button(onClick = onRemove, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) { Text("Remove", color = Color.White) }
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) { Text("Cancel", color = Color.White) }
            Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) { Text("Save", color = Color.White) }
        }
    }
}
