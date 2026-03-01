package com.riprog.launcher.logic.managers

import com.riprog.launcher.ui.activities.MainActivity

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import java.util.concurrent.Executors

class WidgetManager(
    private val activity: MainActivity,
    private val settingsManager: SettingsManager,
    private val appWidgetManager: AppWidgetManager?,
    private val appWidgetHost: AppWidgetHost
) {
    private val widgetPreviewExecutor = Executors.newFixedThreadPool(4)

}