package com.riprog.launcher.data.model

data class LauncherSettings(
    val columns: Int = 4,
    val widgetId: Int = -1,
    val isFreeformHome: Boolean = false,
    val iconScale: Float = 1.0f,
    val isHideLabels: Boolean = false,
    val themeMode: String = "system",
    val isLiquidGlass: Boolean = false,
    val isDarkenWallpaper: Boolean = false,
    val drawerOpenCount: Int = 0,
    val lastDefaultPromptTimestamp: Long = 0L,
    val defaultPromptCount: Int = 0
)
