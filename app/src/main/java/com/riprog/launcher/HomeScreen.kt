package com.riprog.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

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
    val pageCount = settingsManager.pageCount
    val pagerState = rememberPagerState(pageCount = { pageCount })

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -20 && !isDrawerOpen) isDrawerOpen = true
                    if (dragAmount > 20 && isDrawerOpen) isDrawerOpen = false
                }
            }
    ) {
        HomeScreen(
            settingsManager = settingsManager,
            model = model,
            homeItems = homeItems,
            allApps = allApps,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager,
            pagerState = pagerState,
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
                    val newItem = HomeItem.createApp(app.packageName, app.className, 0f, 0f, pagerState.currentPage)
                    val newList = homeItems.toMutableList()
                    newList.add(newItem)
                    homeItems = newList
                    transformingItem = newItem
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
                pagerState = pagerState,
                onSave = {
                    settingsManager.saveHomeItems(homeItems, settingsManager.pageCount)
                    transformingItem = null
                },
                onRemove = {
                    val newList = homeItems.toMutableList()
                    newList.remove(transformingItem)
                    homeItems = newList
                    settingsManager.saveHomeItems(homeItems, settingsManager.pageCount)
                    transformingItem = null
                },
                onCancel = { transformingItem = null },
                onMove = {
                    val newList = homeItems.toMutableList()
                    homeItems = newList
                }
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

    }
}

fun isDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
    val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

@Composable
fun HomeScreen(
    settingsManager: SettingsManager,
    model: LauncherModel?,
    homeItems: List<HomeItem>,
    allApps: List<AppItem>,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onItemClick: (HomeItem) -> Unit,
    onItemLongClick: (HomeItem) -> Unit,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pageCount = settingsManager.pageCount
    var showHomeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }

    Box(modifier = modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(onLongPress = { showHomeMenu = true })
        }
    ) {
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

        if (showHomeMenu) {
            HomeMenu(
                onDismiss = { showHomeMenu = false },
                onSettings = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                    showHomeMenu = false
                },
                onWidgets = { showHomeMenu = false },
                onWallpaper = {
                    try {
                        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.title_select_wallpaper)))
                    } catch (e: Exception) {
                    }
                    showHomeMenu = false
                },
                settingsManager = settingsManager
            )
        }
    }
}
