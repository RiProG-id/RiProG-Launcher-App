package com.riprog.launcher.ui.activities

import com.riprog.launcher.ui.views.layout.MainLayout
import com.riprog.launcher.ui.views.layout.AutoDimmingBackground
import com.riprog.launcher.ui.views.home.HomeView
import com.riprog.launcher.ui.views.home.HomeMenuOverlay
import com.riprog.launcher.ui.views.folder.FolderViewFactory
import com.riprog.launcher.ui.views.drawer.DrawerView
import com.riprog.launcher.ui.views.drawer.AppDrawerContextMenu
import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.theme.ThemeManager
import com.riprog.launcher.logic.managers.WidgetManager
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.logic.managers.FolderManager
import com.riprog.launcher.logic.controllers.FreeformController
import com.riprog.launcher.logic.utils.WidgetSizingUtils
import com.riprog.launcher.ui.activities.WidgetPickerActivity
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.R
import com.riprog.launcher.LauncherApplication

import androidx.appcompat.app.AppCompatActivity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import android.widget.*
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var model: AppRepository
    lateinit var settingsManager: SettingsManager
    private var autoDimmingBackground: AutoDimmingBackground? = null
    lateinit var folderManager: FolderManager
    private lateinit var folderUI: FolderViewFactory
    lateinit var freeformInteraction: FreeformController
    private var widgetManager: WidgetManager? = null
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    lateinit var mainLayout: MainLayout
    lateinit var homeView: HomeView
    lateinit var drawerView: DrawerView
    private var appInstallReceiver: AppInstallReceiver? = null
    var homeItems: MutableList<HomeItem> = ArrayList()
    var allApps: List<AppItem> = ArrayList()
    private var lastGridCol: Float = 0f
    private var lastGridRow: Float = 0f
    private var pendingSpanX: Int = 2
    private var pendingSpanY: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsManager = SettingsManager(this)
        ThemeManager.applyThemeMode(this, settingsManager.themeMode)
        super.onCreate(savedInstanceState)
        ThemeUtils.updateStatusBarContrast(this)

        val w = window
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT

        WindowCompat.setDecorFitsSystemWindows(w, false)

        model = (application as LauncherApplication).model

        mainLayout = MainLayout(this)
        homeView = HomeView(this)
        drawerView = DrawerView(this)
        drawerView.setColumns(settingsManager.columns)
        drawerView.setOnAppLongClickListener(object : DrawerView.OnAppLongClickListener {
            override fun onAppLongClick(view: View, app: AppItem) {
                showAppDrawerMenu(view, app)
            }
        })

        mainLayout.addView(homeView)
        mainLayout.addView(drawerView)
        drawerView.visibility = View.GONE

        freeformInteraction = FreeformController(this, mainLayout, settingsManager, object : FreeformController.InteractionCallback {
            override fun onSaveState() {
                saveHomeState()
            }

            override fun onRemoveItem(item: HomeItem?, view: View?) {
                this@MainActivity.removeHomeItem(item, view)
            }

            override fun onShowAppInfo(item: HomeItem?) {
                this@MainActivity.showAppInfo(item)
            }
        })

        setContentView(mainLayout)

        autoDimmingBackground = AutoDimmingBackground(this, mainLayout, settingsManager)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetHost.startListening()

        applyDynamicColors()
        folderManager = FolderManager(this, settingsManager)
        folderUI = FolderViewFactory(this, settingsManager)
        widgetManager = WidgetManager(this, settingsManager, AppWidgetManager.getInstance(this), AppWidgetHost(this, APPWIDGET_HOST_ID))
        loadApps()
        registerAppInstallReceiver()

        homeView.post {
            restoreHomeState()
            showDefaultLauncherPrompt()
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo?.activityInfo == null) return false
        return packageName == resolveInfo.activityInfo.packageName
    }

    private fun showDefaultLauncherPrompt() {
        if (isDefaultLauncher()) return

        val lastShown = settingsManager.lastDefaultPromptTimestamp
        val count = settingsManager.defaultPromptCount

        if (System.currentTimeMillis() - lastShown < 24 * 60 * 60 * 1000) return
        if (count >= 5) return

        val prompt = LinearLayout(this)
        prompt.orientation = LinearLayout.VERTICAL
        prompt.setBackgroundResource(R.drawable.glass_bg)
        prompt.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
        prompt.gravity = Gravity.CENTER
        prompt.elevation = dpToPx(8).toFloat()

        val title = TextView(this)
        title.setText(R.string.prompt_default_launcher_title)
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(getColor(R.color.foreground))
        prompt.addView(title)

        val message = TextView(this)
        message.setText(R.string.prompt_default_launcher_message)
        message.setPadding(0, dpToPx(8), 0, dpToPx(16))
        message.gravity = Gravity.CENTER
        message.setTextColor(getColor(R.color.foreground_dim))
        prompt.addView(message)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.END

        val btnLater = TextView(this)
        btnLater.setText(R.string.action_later)
        btnLater.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        btnLater.setTextColor(getColor(R.color.foreground))
        btnLater.setOnClickListener { mainLayout.removeView(prompt) }
        buttons.addView(btnLater)

        val btnSet = TextView(this)
        btnSet.setText(R.string.action_set_default)
        btnSet.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        btnSet.setTextColor(getColor(android.R.color.holo_blue_dark))
        btnSet.setTypeface(null, Typeface.BOLD)
        btnSet.setOnClickListener {
            mainLayout.removeView(prompt)
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        }
        buttons.addView(btnSet)

        prompt.addView(buttons)

        val lp = FrameLayout.LayoutParams(
            dpToPx(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        )
        mainLayout.addView(prompt, lp)

        settingsManager.lastDefaultPromptTimestamp = System.currentTimeMillis()
        settingsManager.incrementDefaultPromptCount()
    }

    fun saveHomeState() {
        settingsManager.saveHomeItems(homeItems)
    }

    private fun restoreHomeState() {
        homeItems = settingsManager.getHomeItems().toMutableList()
        if (homeItems.isEmpty()) {
            setupDefaultHome()
        } else {
            for (item in homeItems) {
                renderHomeItem(item)
            }
        }
    }

    private fun setupDefaultHome() {
        saveHomeState()
    }

    fun renderHomeItem(item: HomeItem?): View? {
        if (item == null) return null
        var view: View? = null
        when (item.type) {
            HomeItem.Type.APP -> view = createAppView(item)
            HomeItem.Type.WIDGET -> view = createWidgetView(item)
            HomeItem.Type.CLOCK -> view = createClockView(item)
            HomeItem.Type.FOLDER -> view = folderUI.createFolderView(
                item,
                true,
                homeView.getCellWidth().toInt(),
                homeView.getCellHeight().toInt()
            )
            else -> {}
        }
        if (view != null) {
            homeView.addItemView(item, view)
            if (item.type == HomeItem.Type.FOLDER) {
                val grid = findGridLayout(view as ViewGroup)
                if (grid != null) refreshFolderPreview(item, grid)
            }
        }
        return view
    }

    fun refreshFolderPreview(folder: HomeItem, grid: GridLayout) {
        grid.removeAllViews()
        if (folder.folderItems == null) return
        val count = Math.min(folder.folderItems.size, 4)
        val scale = settingsManager.iconScale
        val size = (dpToPx(18) * scale).toInt()

        for (i in 0 until count) {
            val sub = folder.folderItems[i]
            val packageName = sub.packageName ?: continue
            val iv = ImageView(this)

            val lp = GridLayout.LayoutParams(
                GridLayout.spec(i / 2, GridLayout.CENTER, 1f),
                GridLayout.spec(i % 2, GridLayout.CENTER, 1f)
            )
            lp.width = size
            lp.height = size
            iv.layoutParams = lp

            model.loadIcon(AppItem.fromPackage(this, packageName)) { bitmap ->
                iv.setImageBitmap(bitmap)
            }
            grid.addView(iv)
        }
    }

    private fun findGridLayout(container: ViewGroup): GridLayout? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is GridLayout) return child
            if (child is ViewGroup) {
                val g = findGridLayout(child)
                if (g != null) return g
            }
        }
        return null
    }

    fun createAppView(item: HomeItem): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER

        val iconView = ImageView(this)
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val scale = settingsManager.iconScale
        val size = (baseSize * scale).toInt()

        val iconParams = LinearLayout.LayoutParams(size, size)
        iconView.layoutParams = iconParams

        val labelView = TextView(this)
        labelView.setTextColor(getColor(R.color.foreground))
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
        labelView.gravity = Gravity.CENTER
        labelView.maxLines = 1
        labelView.ellipsize = TextUtils.TruncateAt.END

        val packageName = item.packageName ?: return container
        val app = findApp(packageName)
        if (app != null) {
            model.loadIcon(app) { bitmap -> iconView.setImageBitmap(bitmap) }
            labelView.text = app.label
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            labelView.text = "..."
        }

        container.addView(iconView)
        container.addView(labelView)
        if (settingsManager.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    private fun createWidgetView(item: HomeItem?): View? {
        if (item == null) return null
        val info = appWidgetManager.getAppWidgetInfo(item.widgetId) ?: return null
        return try {
            val hostView = appWidgetHost.createView(this, item.widgetId, info)
            hostView.setAppWidget(item.widgetId, info)
            hostView
        } catch (e: Exception) {
            null
        }
    }


    fun removeHomeItem(item: HomeItem?, view: View?) {
        homeItems.remove(item)
        if (view != null && view.parent is ViewGroup) {
            (view.parent as ViewGroup).removeView(view)
        }
        saveHomeState()
        homeView.refreshIcons(model, allApps)
    }

    fun showAppInfo(item: HomeItem?) {
        val packageName = item?.packageName
        if (packageName.isNullOrEmpty()) return
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.app_info_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createClockView(item: HomeItem): View {
        val clockRoot = LinearLayout(this)
        clockRoot.orientation = LinearLayout.VERTICAL
        clockRoot.gravity = Gravity.CENTER

        val tvTime = TextView(this)
        tvTime.textSize = 64f
        tvTime.setTextColor(getColor(R.color.foreground))
        tvTime.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)

        val tvDate = TextView(this)
        tvDate.textSize = 18f
        tvDate.setTextColor(getColor(R.color.foreground_dim))
        tvDate.gravity = Gravity.CENTER

        clockRoot.addView(tvTime)
        clockRoot.addView(tvDate)

        val updateTask: Runnable = object : Runnable {
            override fun run() {
                val cal = Calendar.getInstance()
                tvTime.text = DateFormat.getTimeFormat(this@MainActivity).format(cal.time)
                tvDate.text = DateFormat.getMediumDateFormat(this@MainActivity).format(cal.time)
                tvTime.postDelayed(this, 10000)
            }
        }
        tvTime.post(updateTask)
        return clockRoot
    }

    private fun findApp(packageName: String): AppItem? {
        for (app in allApps) {
            if (app.packageName == packageName) return app
        }
        return null
    }


    private fun openWallpaperPicker() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val pickIntent = Intent(Intent.ACTION_GET_CONTENT)
                pickIntent.type = "image/*"
                startActivity(Intent.createChooser(pickIntent, getString(R.string.title_select_wallpaper)))
            } catch (e2: Exception) {
                Toast.makeText(this, getString(R.string.wallpaper_picker_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivityForResult(intent, 100)
    }

    private fun applyDynamicColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val accentColor = resources.getColor(android.R.color.system_accent1_400, theme)
                homeView.setAccentColor(accentColor)
                drawerView.setAccentColor(accentColor)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun loadApps() {
        model.loadApps { apps ->
            this.allApps = apps
            drawerView.setApps(apps, model)
            homeView.refreshIcons(model, apps)
        }
    }

    private fun registerAppInstallReceiver() {
        appInstallReceiver = AppInstallReceiver()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addDataScheme("package")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appInstallReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(appInstallReceiver, filter)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }

    override fun onBackPressed() {
        if (isAnyOverlayVisible()) {
            dismissAllOverlays()
            return
        }
        if (mainLayout.closeDrawer()) {
            return
        }
        // Don't call super.onBackPressed() to prevent closing the launcher
    }

    override fun onResume() {
        super.onResume()
        autoDimmingBackground?.updateDimVisibility()
        homeView.refreshLayout()
        homeView.refreshIcons(model, allApps)
        drawerView.updateTheme()
        folderManager.updateTheme()
        (currentHomeMenu as? HomeMenuOverlay)?.updateTheme()
        (currentAppDrawerMenu as? AppDrawerContextMenu)?.updateTheme()
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (appInstallReceiver != null) unregisterReceiver(appInstallReceiver)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyDynamicColors()
        ThemeUtils.updateStatusBarContrast(this, newConfig)
        autoDimmingBackground?.updateDimVisibility(newConfig)
        homeView.refreshLayout()
        homeView.refreshIcons(model, allApps)
        drawerView.updateTheme(newConfig)
        folderManager.updateTheme(newConfig)
        (currentHomeMenu as? HomeMenuOverlay)?.updateTheme(newConfig)
        (currentAppDrawerMenu as? AppDrawerContextMenu)?.updateTheme(newConfig)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            loadApps()
            homeView.refreshLayout()
            return
        }
        if (requestCode == REQUEST_PICK_WIDGET_SCREEN && resultCode == RESULT_OK && data != null) {
            val info = data.getParcelableExtra<AppWidgetProviderInfo>("EXTRA_WIDGET_INFO")
            val spanX = data.getIntExtra("EXTRA_SPAN_X", 2)
            val spanY = data.getIntExtra("EXTRA_SPAN_Y", 1)
            if (info != null) {
                spawnWidget(info, spanX, spanY)
            }
            return
        }
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                configureWidget(data)
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                createWidget(data)
            }
        }
    }

    private fun configureWidget(data: Intent) {
        val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return
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

        val sX = pendingSpanX.coerceAtMost(settingsManager.columns)
        val sY = pendingSpanY.coerceAtMost(HomeView.GRID_ROWS)

        var col = maxOf(0, (settingsManager.columns - sX) / 2)
        var row = maxOf(0, (HomeView.GRID_ROWS - sY) / 2)

        if (col + sX > settingsManager.columns) col = settingsManager.columns - sX
        if (row + sY > HomeView.GRID_ROWS) row = HomeView.GRID_ROWS - sY

        if (!settingsManager.isFreeformHome && !homeView.doesFit(sX, sY, col, row, homeView.currentPage)) {
            val occupied = homeView.getOccupiedCells(homeView.currentPage)
            val nearest = findNearestAvailable(occupied, row, col, sX, sY)
            if (nearest != null) {
                row = nearest.first
                col = nearest.second
            }
        }

        val item = HomeItem.createWidget(appWidgetId, col.toFloat(), row.toFloat(), sX, sY, homeView.currentPage)
        homeItems.add(item)
        renderHomeItem(item)
        saveHomeState()
    }

    fun pickWidget() {
        val intent = Intent(this, WidgetPickerActivity::class.java)
        startActivityForResult(intent, REQUEST_PICK_WIDGET_SCREEN)
    }

    fun spawnWidget(info: AppWidgetProviderInfo, spanX: Int, spanY: Int) {
        val sX = spanX.coerceAtMost(settingsManager.columns)
        val sY = spanY.coerceAtMost(HomeView.GRID_ROWS)

        var col = maxOf(0, (settingsManager.columns - sX) / 2)
        var row = maxOf(0, (HomeView.GRID_ROWS - sY) / 2)

        if (col + sX > settingsManager.columns) col = settingsManager.columns - sX
        if (row + sY > HomeView.GRID_ROWS) row = HomeView.GRID_ROWS - sY

        pendingSpanX = sX
        pendingSpanY = sY

        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        if (allowed) {
            if (!settingsManager.isFreeformHome && !homeView.doesFit(sX, sY, col, row, homeView.currentPage)) {
                val occupied = homeView.getOccupiedCells(homeView.currentPage)
                val nearest = findNearestAvailable(occupied, row, col, sX, sY)
                if (nearest != null) {
                    row = nearest.first
                    col = nearest.second
                }
            }

            val item = HomeItem.createWidget(appWidgetId, col.toFloat(), row.toFloat(), sX, sY, homeView.currentPage)

            homeItems.add(item)
            renderHomeItem(item)
            saveHomeState()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            startActivityForResult(intent, REQUEST_PICK_APPWIDGET)
        }
    }

    fun startNewWidgetDrag(info: AppWidgetProviderInfo, spanX: Int, spanY: Int) {
        val sX = spanX
        val sY = spanY

        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        if (allowed) {
            val item = HomeItem.createWidget(appWidgetId, 0f, 0f, sX, sY, homeView.currentPage)
            val view = createWidgetView(item)
            if (view != null) {
                view.tag = item
                mainLayout.startExternalDrag(view)
            }
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            startActivityForResult(intent, REQUEST_PICK_APPWIDGET)
        }
    }

    fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    fun handleItemClick(v: View) {
        mainLayout.handleItemClick(v)
    }

    fun handleAppLaunch(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) startActivity(intent)
    }

    fun isAnyOverlayVisible(): Boolean {
        return currentHomeMenu != null || currentAppDrawerMenu != null || folderManager.isFolderOpen()
    }

    var lastOverlayDismissTime: Long = 0

    fun dismissAllOverlays() {
        if (currentHomeMenu != null) {
            mainLayout.removeView(currentHomeMenu)
            currentHomeMenu = null
            lastOverlayDismissTime = System.currentTimeMillis()
        }
        if (currentAppDrawerMenu != null) {
            mainLayout.removeView(currentAppDrawerMenu)
            currentAppDrawerMenu = null
            lastOverlayDismissTime = System.currentTimeMillis()
        }
        if (folderManager.isFolderOpen()) {
            folderManager.closeFolder()
            lastOverlayDismissTime = System.currentTimeMillis()
        }
    }

    fun showHomeMenu(x: Float, y: Float) {
        if (isAnyOverlayVisible()) return
        val menu = HomeMenuOverlay(this, settingsManager, object : HomeMenuOverlay.Callback {
            override fun onAddPageLeft() {
                homeView.addPageLeft()
                homeView.scrollToPage(0)
            }

            override fun onAddPageRight() {
                homeView.addPageRight()
                homeView.scrollToPage(homeView.pages.size - 1)
            }

            override fun onRemovePage() {
                homeView.onRemovePage()
            }

            override fun getPageCount(): Int {
                return homeView.getPageCount()
            }

            override fun onPickWidget() {
                pickWidget()
            }

            override fun onOpenWallpaper() {
                openWallpaperPicker()
            }

            override fun onOpenSettings() {
                openSettings()
            }

            override fun dismiss() {
                if (currentHomeMenu != null) {
                    mainLayout.removeView(currentHomeMenu)
                    currentHomeMenu = null
                    lastOverlayDismissTime = System.currentTimeMillis()
                }
            }
        })
        currentHomeMenu = menu
        mainLayout.addView(menu, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private var currentHomeMenu: View? = null
    private var currentAppDrawerMenu: View? = null

    fun showAppDrawerMenu(anchor: View, app: AppItem) {
        if (isAnyOverlayVisible()) return
        val menu = AppDrawerContextMenu(this, settingsManager, object : AppDrawerContextMenu.Callback {
            override fun onAddToHome() {
                spawnApp(app)
                mainLayout.closeDrawer()
            }

            override fun onAppInfo() {
                showAppInfo(HomeItem.createApp(app.packageName, app.className, 0f, 0f, 0))
            }

            override fun dismiss() {
                if (currentAppDrawerMenu != null) {
                    mainLayout.removeView(currentAppDrawerMenu)
                    currentAppDrawerMenu = null
                    lastOverlayDismissTime = System.currentTimeMillis()
                }
            }
        })
        currentAppDrawerMenu = menu
        menu.showAt(anchor, mainLayout)
    }

    private fun findNearestAvailable(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        var minDest = Double.MAX_VALUE
        var bestPos: Pair<Int, Int>? = null
        val columns = settingsManager.columns

        for (i in 0..HomeView.GRID_ROWS - spanY) {
            for (j in 0..columns - spanX) {
                var canPlace = true
                for (ri in i until i + spanY) {
                    for (ci in j until j + spanX) {
                        if (ri >= HomeView.GRID_ROWS || ci >= columns || occupied[ri][ci]) {
                            canPlace = false
                            break
                        }
                    }
                    if (!canPlace) break
                }

                if (canPlace) {
                    val d = Math.sqrt(Math.pow((i - r).toDouble(), 2.0) + Math.pow((j - c).toDouble(), 2.0))
                    if (d < minDest) {
                        minDest = d
                        bestPos = Pair(i, j)
                    }
                }
            }
        }
        return bestPos
    }

    private fun findFirstAvailableSlot(spanX: Int, spanY: Int): Triple<Int, Int, Int>? {
        val startPage = homeView.currentPage
        val pageCount = homeView.getPageCount()

        for (p in startPage until pageCount) {
            val occupied = homeView.getOccupiedCells(p)
            for (r in 0..HomeView.GRID_ROWS - spanY) {
                for (c in 0..settingsManager.columns - spanX) {
                    var canPlace = true
                    for (ri in r until r + spanY) {
                        for (ci in c until c + spanX) {
                            if (ri >= HomeView.GRID_ROWS || ci >= settingsManager.columns || occupied[ri][ci]) {
                                canPlace = false
                                break
                            }
                        }
                        if (!canPlace) break
                    }
                    if (canPlace) return Triple(p, r, c)
                }
            }
        }

        for (p in 0 until startPage) {
            val occupied = homeView.getOccupiedCells(p)
            for (r in 0..HomeView.GRID_ROWS - spanY) {
                for (c in 0..settingsManager.columns - spanX) {
                    var canPlace = true
                    for (ri in r until r + spanY) {
                        for (ci in c until c + spanX) {
                            if (ri >= HomeView.GRID_ROWS || ci >= settingsManager.columns || occupied[ri][ci]) {
                                canPlace = false
                                break
                            }
                        }
                        if (!canPlace) break
                    }
                    if (canPlace) return Triple(p, r, c)
                }
            }
        }

        return null
    }

    fun spawnApp(app: AppItem) {
        var slot = findFirstAvailableSlot(1, 1)
        if (slot == null) {
            homeView.addPage()
            slot = Triple(homeView.getPageCount() - 1, 0, 0)
        }

        val item = HomeItem.createApp(app.packageName, app.className, slot.third.toFloat(), slot.second.toFloat(), slot.first)
        homeItems.add(item)
        renderHomeItem(item)
        saveHomeState()

        homeView.scrollToPage(slot.first)
        Toast.makeText(this, getString(R.string.app_added_to_home, app.label), Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private inner class AppInstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
    }

    companion object {
        private const val REQUEST_PICK_APPWIDGET = 1
        private const val REQUEST_CREATE_APPWIDGET = 2
        private const val REQUEST_PICK_WIDGET_SCREEN = 3
        private const val APPWIDGET_HOST_ID = 1024
    }
}
