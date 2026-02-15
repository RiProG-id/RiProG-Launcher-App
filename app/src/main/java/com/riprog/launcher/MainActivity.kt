package com.riprog.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {

    private var model: LauncherModel? = null
    private lateinit var settingsManager: SettingsManager
    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null
    private var appInstallReceiver: AppInstallReceiver? = null
    private val homeItems = mutableStateListOf<HomeItem>()

    private val configureWidgetLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { createWidget(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        enableEdgeToEdge()

        homeItems.addAll(settingsManager.getHomeItems())
        model = (application as LauncherApplication).getModel()
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetHost!!.startListening()

        registerAppInstallReceiver()

        setContent {
            MainScreen(
                settingsManager = settingsManager,
                model = model,
                appWidgetHost = appWidgetHost,
                appWidgetManager = appWidgetManager,
                homeItems = homeItems
            )
        }
    }

    private fun registerAppInstallReceiver() {
        appInstallReceiver = AppInstallReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appInstallReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(appInstallReceiver, filter)
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost?.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        appInstallReceiver?.let { unregisterReceiver(it) }
    }

    private fun configureWidget(data: Intent) {
        val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val info = appWidgetManager?.getAppWidgetInfo(appWidgetId) ?: return
        if (info.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            intent.component = info.configure
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            configureWidgetLauncher.launch(intent)
        } else {
            createWidget(data)
        }
    }

    private fun createWidget(data: Intent) {
        val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val newItem = HomeItem.createWidget(appWidgetId, 0f, 0f, 2f, 2f, 0)
        homeItems.add(newItem)
        settingsManager.saveHomeItems(homeItems.toList(), settingsManager.pageCount)
    }

    private inner class AppInstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val data = intent.data ?: return
            val packageName = data.schemeSpecificPart
            model?.invalidateAppListCache()
            if (Intent.ACTION_PACKAGE_REPLACED == action) {
                model?.clearAppIconCache(packageName)
            }
        }
    }

    companion object {
        private const val APPWIDGET_HOST_ID = 1024
    }
}
