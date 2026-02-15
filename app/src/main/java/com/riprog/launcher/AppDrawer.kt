package com.riprog.launcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.*
@Composable
fun AppDrawer(
    apps: List<AppItem>,
    settingsManager: SettingsManager,
    model: LauncherModel?,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        LauncherModel.filterApps(apps, searchQuery)
    }
    val gridState = rememberLazyGridState()
    val numColumns = settingsManager.columns
    val iconScale = settingsManager.iconScale
    val isAtTop = remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(isAtTop.value) {
        onScrollStateChanged(isAtTop.value)
    }
    val adaptiveColorInt = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
    val adaptiveColor = Color(adaptiveColorInt)
    val secondaryColor = adaptiveColor.copy(alpha = 0.5f)
    val isLiquidGlass = settingsManager.isLiquidGlass()
    val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    val backgroundColor = if (isLiquidGlass) {
        Color(context.getColor(R.color.background))
    } else {
        if (isNight) Color.Black else Color.White
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .statusBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_hint),
                            color = secondaryColor
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = secondaryColor
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = if (isLiquidGlass) 0.1f else 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = if (isLiquidGlass) 0.1f else 0.05f),
                        cursorColor = adaptiveColor,
                        focusedTextColor = adaptiveColor,
                        unfocusedTextColor = adaptiveColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(numColumns),
                    state = gridState,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredApps) { index, app ->
                        AppItemView(
                            app = app,
                            model = model,
                            iconScale = iconScale,
                            textColor = adaptiveColor,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }
            AlphabetIndexBar(
                onLetterSelected = { letter ->
                    val index = filteredApps.indexOfFirst {
                        it.label.uppercase(Locale.getDefault()).startsWith(letter)
                    }
                    if (index != -1) {
                        coroutineScope.launch {
                            gridState.animateScrollToItem(index)
                        }
                    }
                },
                color = secondaryColor
            )
        }
    }
}
@Composable
fun AppItemView(
    app: AppItem,
    model: LauncherModel?,
    iconScale: Float,
    textColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val baseIconSize = 48.dp
    val iconSize = baseIconSize * iconScale
    var iconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(app) {
        model?.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
            override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                iconBitmap = icon
            }
        })
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .pointerInput(app) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = iconBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                )
            }
        }
        Text(
            text = app.label,
            color = textColor,
            fontSize = (10 * iconScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
        )
    }
}
@Composable
fun AlphabetIndexBar(
    onLetterSelected: (String) -> Unit,
    color: Color
) {
    val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("").filter { it.isNotEmpty() }
    Column(
        modifier = Modifier
            .width(30.dp)
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alphabet.forEach { letter ->
            Text(
                text = letter,
                fontSize = 10.sp,
                color = color,
                modifier = Modifier
                    .clickable { onLetterSelected(letter) }
                    .padding(vertical = 2.dp)
            )
        }
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
    val foregroundDim = Color(context.getColor(R.color.foreground_dim))
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { i ->
            val color = if (i == currentPage) accentColor else foregroundDim.copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(6.dp)
                    .background(color, shape = CircleShape)
            )
        }
    }
}
