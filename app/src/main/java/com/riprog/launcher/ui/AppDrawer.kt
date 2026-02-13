package com.riprog.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riprog.launcher.AppItem
import com.riprog.launcher.LauncherModel
import com.riprog.launcher.R
import com.riprog.launcher.SettingsManager
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    apps: List<AppItem>,
    model: LauncherModel,
    settingsManager: SettingsManager,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit,
    accentColor: Int,
    systemInsets: PaddingValues = PaddingValues(0.dp)
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        LauncherModel.filterApps(apps, searchQuery)
    }

    val context = LocalContext.current
    val bgColor = Color(context.getColor(R.color.background))
    val isLiquidGlass = settingsManager.isLiquidGlass

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isLiquidGlass) bgColor.copy(alpha = 0.8f) else bgColor,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .padding(systemInsets)
    ) {
        Column {
            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                accentColor = Color(accentColor),
                isLiquidGlass = isLiquidGlass,
                modifier = Modifier.padding(16.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                val numColumns = settingsManager.columns
                val iconScale = settingsManager.iconScale

                val rows = remember(filteredApps, numColumns) {
                    filteredApps.chunked(numColumns)
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rows) { rowApps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            rowApps.forEach { app ->
                                AppItemView(
                                    app = app,
                                    model = model,
                                    iconScale = iconScale,
                                    onClick = {
                                        focusManager.clearFocus()
                                        onAppClick(app)
                                    },
                                    onLongClick = {
                                        focusManager.clearFocus()
                                        onAppLongClick(app)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Fill empty slots
                            repeat(numColumns - rowApps.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Index Bar
                IndexBar(
                    onLetterClick = { letter ->
                        val firstAppWithLetter = filteredApps.indexOfFirst {
                            it.label.firstOrNull()?.uppercaseChar()?.toString() == letter
                        }
                        if (firstAppWithLetter != -1) {
                            coroutineScope.launch {
                                lazyListState.animateScrollToItem(firstAppWithLetter / numColumns)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    accentColor: Color,
    isLiquidGlass: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(context.getColor(R.color.search_background)),
        shape = RoundedCornerShape(12.dp),
        elevation = if (isLiquidGlass) 0.dp else 2.dp
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_hint),
                    color = accentColor.copy(alpha = 0.5f)
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = Color(context.getColor(R.color.foreground_dim))
                )
            },
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                textColor = Color(context.getColor(R.color.foreground))
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { /* Handle search */ }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AppItemView(
    app: AppItem,
    model: LauncherModel,
    iconScale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var iconBitmap by remember(app) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(app) {
        model.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
            override fun onIconLoaded(icon: Bitmap?) {
                iconBitmap = icon
            }
        })
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Box(
            modifier = Modifier.size((48 * iconScale).dp),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Placeholder
                Icon(
                    painter = painterResource(android.R.drawable.sym_def_app_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = app.label,
            color = Color(context.getColor(R.color.foreground)),
            fontSize = (10 * iconScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun IndexBar(
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { it.toString() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alphabet.forEach { letter ->
            Text(
                text = letter,
                fontSize = 10.sp,
                color = Color(context.getColor(R.color.foreground_dim)),
                modifier = Modifier
                    .padding(vertical = 1.dp)
                    .clickable { onLetterClick(letter) }
            )
        }
    }
}
