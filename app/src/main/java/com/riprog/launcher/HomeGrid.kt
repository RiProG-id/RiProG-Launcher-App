package com.riprog.launcher

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager

const val GRID_COLUMNS = 4
const val GRID_ROWS = 6

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
