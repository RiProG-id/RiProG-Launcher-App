package com.riprog.launcher

import android.app.AlertDialog
import android.app.UiModeManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.riprog.launcher.ui.AppDrawer
import com.riprog.launcher.ui.HomeScreen
import com.riprog.launcher.ui.TransformOverlay
import com.riprog.launcher.ui.FolderOverlay
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_PICK_APPWIDGET = 1
        private const val REQUEST_CREATE_APPWIDGET = 2
        private const val APPWIDGET_HOST_ID = 1024
    }

    private lateinit var model: LauncherModel
    private lateinit var settingsManager: SettingsManager
    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null

    private var homeItems = mutableStateListOf<HomeItem>()
    private var allApps = mutableStateOf<List<AppItem>>(emptyList())
    private var isDrawerOpen by mutableStateOf(false)
    private var accentColor by mutableIntStateOf(android.graphics.Color.WHITE)
    private var currentTransformItem by mutableStateOf<HomeItem?>(null)
    private var currentFolderItem by mutableStateOf<HomeItem?>(null)

    private var lastGridCol: Float = 0f
    private var lastGridRow: Float = 0f

    private val appInstallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val data = intent.data
            val packageName = data?.schemeSpecificPart ?: return

            model.invalidateAppListCache()
            if (Intent.ACTION_PACKAGE_REPLACED == action) {
                model.clearAppIconCache(packageName)
            }

            if (Intent.ACTION_PACKAGE_REMOVED == action) {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    removePackageItems(packageName)
                }
            }
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        applyThemeMode(settingsManager.themeMode ?: "system")

        window.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }
        }

        model = (application as LauncherApplication).model!!

        setContent {
            MainUI()
        }

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetHost?.startListening()

        loadApps()
        registerAppInstallReceiver()
        restoreHomeState()

        window.decorView.post {
            showDefaultLauncherPrompt()
        }
    }

    @Composable
    fun MainUI() {
        val apps by allApps
        val items = homeItems

        Box(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (!isDrawerOpen && currentFolderItem == null && currentTransformItem == null) {
                        if (dragAmount.y < -50 && abs(dragAmount.y) > abs(dragAmount.x)) {
                            isDrawerOpen = true
                        }
                    }
                }
            }
        ) {
            HomeScreen(
                homeItems = items,
                model = model,
                settingsManager = settingsManager,
                onItemClick = { item -> handleItemClick(item) },
                onItemLongClick = { item, offset ->
                    if (settingsManager.isFreeformHome) {
                        currentTransformItem = item
                    }
                },
                onItemMove = { item, page, col, row ->
                    item.page = page
                    item.col = col
                    item.row = row
                },
                onItemDropped = { saveHomeState() },
                accentColor = accentColor
            )

            if (isDrawerOpen) {
                AppDrawer(
                    apps = apps,
                    model = model,
                    settingsManager = settingsManager,
                    onAppClick = { app ->
                        handleAppLaunch(app.packageName)
                        isDrawerOpen = false
                    },
                    onAppLongClick = { app ->
                        isDrawerOpen = false
                        val newItem = HomeItem.createApp(app.packageName, app.className, 0f, 0f, 0)
                        homeItems.add(newItem)
                        saveHomeState()
                        // In 1:1, this should start a drag, but adding to home is a safe fallback
                    },
                    accentColor = accentColor
                )
            }

            currentTransformItem?.let { item ->
                TransformOverlay(
                    item = item,
                    onSave = {
                        saveHomeState()
                        currentTransformItem = null
                    },
                    onCancel = { currentTransformItem = null },
                    onRemove = {
                        homeItems.remove(item)
                        saveHomeState()
                        currentTransformItem = null
                    },
                    onAppInfo = {
                        showAppInfo(item.packageName)
                        currentTransformItem = null
                    },
                    settingsManager = settingsManager
                )
            }

            currentFolderItem?.let { folder ->
                FolderOverlay(
                    folder = folder,
                    model = model,
                    settingsManager = settingsManager,
                    onAppClick = { pkg -> handleAppLaunch(pkg) },
                    onClose = {
                        saveHomeState()
                        currentFolderItem = null
                    }
                )
            }
        }
    }

    private fun handleAppLaunch(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun showAppInfo(packageName: String?) {
        if (packageName.isNullOrEmpty()) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (ignored: Exception) {
        }
    }

    private fun handleItemClick(item: HomeItem) {
        when (item.type) {
            HomeItem.Type.APP -> handleAppLaunch(item.packageName ?: "")
            HomeItem.Type.FOLDER -> { currentFolderItem = item }
            HomeItem.Type.WIDGET -> showWidgetOptions(item)
            else -> {}
        }
    }

    private fun showWidgetOptions(item: HomeItem) {
        val options = arrayOf(getString(R.string.action_resize), getString(R.string.action_remove))
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(options) { _, which ->
                if (which == 0) showResizeDialog(item)
                else removeHomeItem(item)
            }.create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager))
    }

    private fun showResizeDialog(item: HomeItem) {
        val sizes = arrayOf("1x1", "2x1", "2x2", "4x2", "4x1")
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_resize_widget)
            .setItems(sizes) { _, which ->
                when (which) {
                    0 -> { item.spanX = 1; item.spanY = 1 }
                    1 -> { item.spanX = 2; item.spanY = 1 }
                    2 -> { item.spanX = 2; item.spanY = 2 }
                    3 -> { item.spanX = 4; item.spanY = 2 }
                    4 -> { item.spanX = 4; item.spanY = 1 }
                }
                saveHomeState()
                restoreHomeState()
            }.create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager))
    }

    private fun removeHomeItem(item: HomeItem) {
        homeItems.remove(item)
        saveHomeState()
    }

    private fun loadApps() {
        model.loadApps(object : LauncherModel.OnAppsLoadedListener {
            override fun onAppsLoaded(apps: List<AppItem>) {
                allApps.value = apps
            }
        })
    }

    private fun restoreHomeState() {
        val items = settingsManager.getHomeItems()
        homeItems.clear()
        homeItems.addAll(items)
    }

    fun saveHomeState() {
        settingsManager.saveHomeItems(homeItems.toList())
    }

    private fun removePackageItems(packageName: String) {
        homeItems.removeAll { it.packageName == packageName }
        saveHomeState()
    }

    private fun registerAppInstallReceiver() {
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

    private fun applyThemeMode(mode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val nightMode = when (mode) {
                "light" -> UiModeManager.MODE_NIGHT_NO
                "dark" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            if (uiModeManager.nightMode != nightMode) {
                uiModeManager.setApplicationNightMode(nightMode)
            }
        }
    }

    private fun showDefaultLauncherPrompt() {
        if (isDefaultLauncher()) return
        val lastShown = settingsManager.lastDefaultPromptTimestamp
        val count = settingsManager.defaultPromptCount
        if (System.currentTimeMillis() - lastShown < 24 * 60 * 60 * 1000) return
        if (count >= 5) return

        val prompt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeUtils.getGlassDrawable(this@MainActivity, settingsManager, 12f)
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            gravity = Gravity.CENTER
            elevation = dpToPx(8).toFloat()
        }

        prompt.addView(TextView(this).apply {
            setText(R.string.prompt_default_launcher_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.foreground))
        })

        prompt.addView(TextView(this).apply {
            setText(R.string.prompt_default_launcher_message)
            setPadding(0, dpToPx(8), 0, dpToPx(16))
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.foreground_dim))
        })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        buttons.addView(TextView(this).apply {
            setText(R.string.action_later)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            setTextColor(getColor(R.color.foreground))
            setOnClickListener { (prompt.parent as? ViewGroup)?.removeView(prompt) }
        })

        buttons.addView(TextView(this).apply {
            setText(R.string.action_set_default)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            setTextColor(getColor(android.R.color.holo_blue_dark))
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                (prompt.parent as? ViewGroup)?.removeView(prompt)
                startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
            }
        })
        prompt.addView(buttons)

        addContentView(prompt, FrameLayout.LayoutParams(dpToPx(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        settingsManager.lastDefaultPromptTimestamp = System.currentTimeMillis()
        settingsManager.incrementDefaultPromptCount()
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return packageName == resolveInfo?.activityInfo?.packageName
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            loadApps()
            restoreHomeState()
            return
        }
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                REQUEST_PICK_APPWIDGET -> configureWidget(data)
                REQUEST_CREATE_APPWIDGET -> createWidget(data)
            }
        }
    }

    private fun configureWidget(data: Intent) {
        val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val info = appWidgetManager?.getAppWidgetInfo(appWidgetId) ?: return
        if (info.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            intent.component = info.configure
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET)
        } else {
            createWidget(data)
        }
    }

    private fun createWidget(data: Intent) {
        val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val item = HomeItem.createWidget(appWidgetId, lastGridCol, lastGridRow, 2, 1, 0)
        homeItems.add(item)
        saveHomeState()
    }

    fun pickWidget() {
        val providers = appWidgetManager?.installedProviders ?: return
        val grouped = providers.groupBy { it.provider.packageName }
        val scrollView = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }
        scrollView.addView(root)

        val packages = grouped.keys.sortedWith(compareBy { getAppName(it) })
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_pick_widget)
            .setView(scrollView)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        packages.forEach { pkg ->
            root.addView(TextView(this).apply {
                text = getAppName(pkg)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(getColor(R.color.foreground))
                setPadding(0, dpToPx(16), 0, dpToPx(8))
            })

            grouped[pkg]?.forEach { info ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                    isClickable = true
                    setBackgroundResource(android.R.drawable.list_selector_background)
                }

                val preview = ImageView(this)
                val spanX = Math.max(1, info.minWidth / (resources.displayMetrics.widthPixels / 4))
                val spanY = Math.max(1, info.minHeight / (resources.displayMetrics.heightPixels / 6))
                preview.setImageDrawable(info.loadPreviewImage(this, 0) ?: info.loadIcon(this, 0))
                preview.scaleType = ImageView.ScaleType.FIT_CENTER
                preview.background = GradientDrawable().apply {
                    setColor(getColor(R.color.search_background))
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), getColor(R.color.foreground_dim))
                }
                item.addView(preview, LinearLayout.LayoutParams(dpToPx(60), dpToPx(60)).apply { rightMargin = dpToPx(12) })

                val textLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                textLayout.addView(TextView(this).apply { text = info.label; setTextColor(getColor(R.color.foreground)) })
                textLayout.addView(TextView(this).apply {
                    text = getString(R.string.widget_size_format, spanX, spanY)
                    textSize = 12f
                    setTextColor(getColor(R.color.foreground_dim))
                })
                item.addView(textLayout)

                item.setOnClickListener {
                    dialog.dismiss()
                    val appWidgetId = appWidgetHost?.allocateAppWidgetId() ?: return@setOnClickListener
                    if (appWidgetManager?.bindAppWidgetIdIfAllowed(appWidgetId, info.provider) == true) {
                        val homeItem = HomeItem.createWidget(appWidgetId, lastGridCol, lastGridRow, spanX, spanY, 0)
                        homeItems.add(homeItem)
                        saveHomeState()
                    } else {
                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                        startActivityForResult(intent, REQUEST_PICK_APPWIDGET)
                    }
                }
                root.addView(item)
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager))
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    fun showHomeContextMenu(col: Float, row: Float, page: Int) {
        val options = arrayOf(getString(R.string.menu_widgets), getString(R.string.menu_wallpaper), getString(R.string.menu_settings))
        val icons = intArrayOf(R.drawable.ic_widgets, R.drawable.ic_wallpaper, R.drawable.ic_settings)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.select_dialog_item, android.R.id.text1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<TextView>(android.R.id.text1)
                tv.setCompoundDrawablesWithIntrinsicBounds(icons[position], 0, 0, 0)
                tv.compoundDrawablePadding = dpToPx(16)
                tv.setTextColor(getColor(R.color.foreground))
                tv.compoundDrawables[0]?.setTint(getColor(R.color.foreground))
                return view
            }
        }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_home_menu)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> { lastGridCol = col; lastGridRow = row; pickWidget() }
                    1 -> openWallpaperPicker()
                    2 -> startActivityForResult(Intent(this, SettingsActivity::class.java), 100)
                }
            }.create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager))
    }

    private fun openWallpaperPicker() {
        try {
            startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
        } catch (e: Exception) {
            try {
                val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
                startActivity(Intent.createChooser(pickIntent, getString(R.string.title_select_wallpaper)))
            } catch (e2: Exception) {
                Toast.makeText(this, getString(R.string.wallpaper_picker_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isDrawerOpen = false
        currentFolderItem = null
        currentTransformItem = null
    }

    override fun onBackPressed() {
        if (currentTransformItem != null) {
            currentTransformItem = null
        } else if (currentFolderItem != null) {
            currentFolderItem = null
        } else if (isDrawerOpen) {
            isDrawerOpen = false
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeUtils.updateStatusBarContrast(this)
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
        try {
            unregisterReceiver(appInstallReceiver)
        } catch (ignored: Exception) {}
    }
}
