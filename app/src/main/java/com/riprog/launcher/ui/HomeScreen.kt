package com.riprog.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.riprog.launcher.*
import com.riprog.launcher.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeItems: List<HomeItem>,
    model: LauncherModel,
    settingsManager: SettingsManager,
    onItemClick: (HomeItem) -> Unit,
    onItemLongClick: (HomeItem, Offset) -> Unit,
    onItemMove: (HomeItem, Int, Float, Float) -> Unit,
    onItemDropped: (HomeItem) -> Unit,
    accentColor: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val pages = remember(homeItems) {
        val maxPage = homeItems.maxOfOrNull { it.page } ?: 0
        val pageMap = homeItems.groupBy { it.page }
        List(maxOf(2, maxPage + 1)) { pageMap[it] ?: emptyList() }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            HomePage(
                items = pages[pageIndex],
                model = model,
                settingsManager = settingsManager,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        PageIndicator(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            accentColor = Color(accentColor),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}

@Composable
fun HomePage(
    items: List<HomeItem>,
    model: LauncherModel,
    settingsManager: SettingsManager,
    onItemClick: (HomeItem) -> Unit,
    onItemLongClick: (HomeItem, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = modifier) {
        val cellWidth = maxWidth / 4f
        val cellHeight = maxHeight / 6f

        items.forEach { item ->
            HomeItemView(
                item = item,
                model = model,
                settingsManager = settingsManager,
                onClick = { onItemClick(item) },
                onLongClick = { offset ->
                    onItemLongClick(item, offset)
                },
                modifier = Modifier
                    .offset(
                        x = (item.col * cellWidth.value).dp,
                        y = (item.row * cellHeight.value).dp
                    )
                    .size(
                        width = (cellWidth.value * item.spanX).dp,
                        height = (cellHeight.value * item.spanY).dp
                    )
                    .graphicsLayer {
                        rotationZ = item.rotation
                        scaleX = item.scaleX
                        scaleY = item.scaleY
                        rotationX = item.tiltX
                        rotationY = item.tiltY
                    }
            )
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        val col = offset.x / (size.width / 4f)
                        val row = offset.y / (size.height / 6f)
                        (context as? MainActivity)?.showHomeContextMenu(col, row, 0)
                    }
                )
            }
        )
    }
}

@Composable
fun HomeItemView(
    item: HomeItem,
    model: LauncherModel,
    settingsManager: SettingsManager,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var iconBitmap by remember(item) { mutableStateOf<Bitmap?>(null) }
    val iconScale = settingsManager.iconScale
    val hideLabels = settingsManager.isHideLabels

    LaunchedEffect(item) {
        if (item.type == HomeItem.Type.APP) {
            val app = AppItem("", item.packageName ?: "", item.className ?: "")
            model.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
                override fun onIconLoaded(icon: Bitmap?) {
                    iconBitmap = icon
                }
            })
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .pointerInput(item) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { offset -> onLongClick(offset) }
                )
            }
    ) {
        when (item.type) {
            HomeItem.Type.APP -> {
                AppIcon(
                    bitmap = iconBitmap,
                    label = if (hideLabels) "" else (item.packageName ?: ""),
                    scale = iconScale,
                    hideLabel = hideLabels,
                    modifier = Modifier.fillMaxSize()
                )
            }
            HomeItem.Type.FOLDER -> {
                FolderPreview(
                    folder = item,
                    model = model,
                    scale = iconScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            HomeItem.Type.WIDGET -> {
                WidgetView(item)
            }
            HomeItem.Type.CLOCK -> {
                ClockView()
            }
            else -> {}
        }
    }
}

@Composable
fun WidgetView(item: HomeItem) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val info = remember(item.widgetId) {
        try {
            appWidgetManager.getAppWidgetInfo(item.widgetId)
        } catch (e: Exception) {
            null
        }
    }

    if (info != null) {
        AndroidView(
            factory = { ctx ->
                AppWidgetHostView(ctx).apply {
                    setAppWidget(item.widgetId, info)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.2f))) {
            Text("Invalid Widget", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun AppIcon(
    bitmap: Bitmap?,
    label: String,
    scale: Float,
    hideLabel: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = modifier.weight(1f).scale(scale)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    painter = painterResource(android.R.drawable.sym_def_app_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = Color.Gray
                )
            }
        }
        if (!hideLabel && label.isNotEmpty()) {
            Text(
                text = label,
                color = Color(context.getColor(R.color.foreground)),
                fontSize = (10 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FolderPreview(
    folder: HomeItem,
    model: LauncherModel,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.scale(scale),
        color = Color(context.getColor(R.color.search_background)),
        shape = RoundedCornerShape(12.dp)
    ) {
        val items = folder.folderItems ?: emptyList()
        val displayItems = items.take(4)

        GridLayoutPreview(columns = 2, modifier = Modifier.padding(4.dp)) {
            displayItems.forEach { subItem ->
                var subBitmap by remember(subItem) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(subItem) {
                    val app = AppItem("", subItem.packageName ?: "", subItem.className ?: "")
                    model.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
                        override fun onIconLoaded(icon: Bitmap?) {
                            subBitmap = icon
                        }
                    })
                }
                Box(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                    if (subBitmap != null) {
                        Image(
                            bitmap = subBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GridLayoutPreview(
    columns: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }
        val itemWidth = constraints.maxWidth / columns
        val itemHeight = constraints.maxHeight / columns

        val itemConstraints = Constraints.fixed(itemWidth, itemHeight)
        val placeables = measurables.map { it.measure(itemConstraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val x = (index % columns) * itemWidth
                val y = (index / columns) * itemHeight
                placeable.placeRelative(x, y)
            }
        }
    }
}

@Composable
fun ClockView() {
    val context = LocalContext.current
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = java.util.Calendar.getInstance()
            time = android.text.format.DateFormat.getTimeFormat(context).format(cal.time)
            date = android.text.format.DateFormat.getMediumDateFormat(context).format(cal.time)
            delay(10000)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = time, fontSize = 64.sp, color = Color(context.getColor(R.color.foreground)))
        Text(text = date, fontSize = 18.sp, color = Color(context.getColor(R.color.foreground_dim)))
    }
}

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { index ->
            val color = if (index == currentPage) accentColor else Color(context.getColor(R.color.foreground_dim)).copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(6.dp)
                    .background(color, CircleShape)
            )
        }
    }
}
