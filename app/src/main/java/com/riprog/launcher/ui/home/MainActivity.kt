package com.riprog.launcher.ui.home

import com.riprog.launcher.LauncherApplication

import com.riprog.launcher.R

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.riprog.launcher.ui.home.manager.FolderManager
import com.riprog.launcher.ui.home.manager.GridManager
import com.riprog.launcher.ui.home.manager.WidgetManager
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.repository.AppLoader
import com.riprog.launcher.receiver.AppInstallReceiver
import com.riprog.launcher.ui.drawer.DrawerView
import com.riprog.launcher.ui.drag.DragController
import com.riprog.launcher.ui.common.Logger
import com.riprog.launcher.ui.drag.TransformOverlay
import com.riprog.launcher.ui.drag.FreeformInteraction
import com.riprog.launcher.ui.home.manager.FolderUI
import com.riprog.launcher.ui.settings.SettingsActivity
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import com.riprog.launcher.ui.common.ThemeUtils
import com.riprog.launcher.ui.common.ThemeMechanism
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity(), MainLayout.Callback, AppInstallReceiver.Callback {

    companion object {
        const val REQUEST_PICK_APPWIDGET = 1
        private const val REQUEST_CREATE_APPWIDGET = 2
        private const val APPWIDGET_HOST_ID = 1024
    }

    private val viewModel: HomeViewModel by viewModel()
    private val preferences: LauncherPreferences by inject()
    private var appLoader: AppLoader? = null
    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null
    var mainLayout: MainLayout? = null
    private var homeView: HomeView? = null
    private var drawerView: DrawerView? = null
    lateinit var folderManager: FolderManager
    lateinit var folderUI: FolderUI
    lateinit var widgetManager: WidgetManager
    lateinit var gridManager: GridManager
    private lateinit var autoDimming: AutoDimmingBackground
    private lateinit var freeformInteraction: FreeformInteraction
    var allApps = mutableListOf<AppItem>()
    private var appInstallReceiver: AppInstallReceiver? = null
    private var homeItems = mutableListOf<HomeItem>()
    private var lastGridCol = 0f
    private var lastGridRow = 0f
    private var pendingWidgetCol = 0f
    private var pendingWidgetRow = 0f
    private var pendingWidgetSpanX = 0f
    private var pendingWidgetSpanY = 0f
    private var pendingWidgetPage = 0
    private var isStateRestored = false

    internal val widgetPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (appWidgetId != -1) {
                configureWidget(appWidgetId, pendingWidgetCol, pendingWidgetRow, pendingWidgetSpanX, pendingWidgetSpanY)
            }
        }
    }

    private val widgetConfigLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (appWidgetId != -1) {
                createWidgetAt(appWidgetId, pendingWidgetCol, pendingWidgetRow, pendingWidgetSpanX, pendingWidgetSpanY, pendingWidgetPage)
            }
        }
    }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loadApps()
        homeView?.refreshLayout()
        homeView?.refreshIcons(appLoader!!, allApps)
    }

    private val debounceHandler = Handler(Looper.getMainLooper())
    private val saveStateRunnable = Runnable { saveHomeStateInternal() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Logger.d("MainActivity onCreate")

        lifecycleScope.launch {
            viewModel.settings.themeMode.collectLatest { mode ->
                applyThemeMode(mode)
            }
        }

        appLoader = (application as LauncherApplication).appLoader

        mainLayout = MainLayout(this, this)
        homeView = HomeView(this, preferences)
        drawerView = DrawerView(this, preferences)
        drawerView?.setColumns(preferences.columns)
        drawerView?.setOnAppLongClickListener(object : DrawerView.OnAppLongClickListener {
            override fun onAppLongClick(app: AppItem) {
                mainLayout?.closeDrawerInstantly()
                val item = HomeItem.createApp(app.packageName, app.className, 0f, 0f, homeView?.getCurrentPage() ?: 0)
                val view = createAppView(item, false)
                if (view != null) {
                    view.tag = item
                    val size = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                    view.layoutParams = FrameLayout.LayoutParams(size * 2, size * 2)
                    mainLayout?.startExternalDrag(view)
                }
            }
        })

        mainLayout?.addView(homeView)
        mainLayout?.addView(drawerView)
        drawerView?.visibility = View.GONE

        mainLayout?.let { layout ->
            ViewCompat.setOnApplyWindowInsetsListener(layout) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                drawerView?.setSystemInsets(bars.left, bars.top, bars.right, bars.bottom)
                homeView?.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        setContentView(mainLayout)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetHost?.startListening()

        gridManager = GridManager()
        folderManager = FolderManager(this, preferences)
        folderUI = FolderUI(this, preferences)
        widgetManager = WidgetManager(this, preferences, appWidgetManager, appWidgetHost)
        autoDimming = AutoDimmingBackground(this, mainLayout!!, preferences)
        freeformInteraction = FreeformInteraction(this, mainLayout!!, preferences, object : FreeformInteraction.InteractionCallback {
            override fun onSaveState() = saveHomeState()
            override fun onRemoveItem(item: HomeItem, view: View) = removeHomeItem(item, view)
            override fun onShowAppInfo(item: HomeItem) = showAppInfo(item)
            override fun onEdgeScroll(x: Float) { homeView?.checkEdgeScrollLoopStart(x) }
            override fun onDragStart(x: Float, page: Int) { homeView?.setInitialDragState(x, page) }
            override fun onCollision(otherView: View) = showTransformOverlay(otherView)
            override fun findItemAt(x: Float, y: Float, exclude: View): View? = findHomeItemAtRoot(x, y, exclude)
            override fun onUninstallItem(item: HomeItem) = uninstallApp(item)
            override fun onUpdateHomeItemFromTransform(v: View) = updateHomeItemFromTransform(v)
            override fun onUpdateViewPosition(item: HomeItem, v: View) { homeView?.updateViewPosition(item, v) }
        })

        applyDynamicColors()
        registerAppInstallReceiver()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (freeformInteraction.isTransforming()) {
                    freeformInteraction.closeTransformOverlay()
                    return
                }
                if (folderManager.isFolderOpen()) {
                    folderManager.closeFolder()
                    return
                }
                if (mainLayout?.isDrawerOpen == true) {
                    mainLayout?.closeDrawer()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        observeViewModel()

        homeView?.post {
            showDefaultLauncherPrompt()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.homeItems.collectLatest { items ->
                homeItems = items.toMutableList()
                homeView?.setHomeItems(homeItems)
                if (homeItems.isEmpty()) {
                    setupDefaultHome()
                } else {
                    homeView?.clearAllItems()
                    for (item in homeItems) {
                        renderHomeItem(item)
                    }
                }
                isStateRestored = true
            }
        }

        lifecycleScope.launch {
            viewModel.installedApps.collectLatest { apps ->
                allApps = apps.toMutableList()
                drawerView?.setApps(allApps, appLoader)
                homeView?.refreshIcons(appLoader!!, allApps)
            }
        }

        lifecycleScope.launch {
            viewModel.settings.columns.collectLatest { cols ->
                gridManager.columns = cols
                homeView?.gridManager?.columns = cols
                drawerView?.setColumns(cols)
                homeView?.refreshLayout()
            }
        }

        lifecycleScope.launch {
            viewModel.settings.iconScale.collectLatest {
                homeView?.refreshLayout()
                homeView?.refreshIcons(appLoader!!, allApps)
                drawerView?.refreshTheme()
            }
        }

        lifecycleScope.launch {
            viewModel.settings.isFreeformHome.collectLatest {
                homeView?.refreshLayout()
            }
        }

        lifecycleScope.launch {
            viewModel.settings.isHideLabels.collectLatest {
                homeView?.refreshIcons(appLoader!!, allApps)
            }
        }

        lifecycleScope.launch {
            viewModel.settings.isLiquidGlass.collectLatest {
                homeView?.refreshIcons(appLoader!!, allApps)
                drawerView?.refreshTheme()
            }
        }

        lifecycleScope.launch {
            viewModel.settings.isDarkenWallpaper.collectLatest {
                autoDimming.updateDimVisibility()
                homeView?.refreshIcons(appLoader!!, allApps)
                drawerView?.refreshTheme()
            }
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return packageName == resolveInfo?.activityInfo?.packageName
    }

    private fun showDefaultLauncherPrompt() {
        if (isDefaultLauncher()) return

        val lastShown = preferences.lastDefaultPromptTimestamp
        val count = preferences.defaultPromptCount

        if (System.currentTimeMillis() - lastShown < 24 * 60 * 60 * 1000) return
        if (count >= 5) return

        val prompt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeUtils.getGlassDrawable(this@MainActivity, preferences, 12f)
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            gravity = Gravity.CENTER
            elevation = dpToPx(8).toFloat()
        }

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, preferences, true)

        val title = TextView(this).apply {
            setText(R.string.prompt_default_launcher_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(adaptiveColor)
        }
        prompt.addView(title)

        val message = TextView(this).apply {
            setText(R.string.prompt_default_launcher_message)
            setPadding(0, dpToPx(8), 0, dpToPx(16))
            gravity = Gravity.CENTER
            setTextColor(adaptiveColor and 0xBBFFFFFF.toInt())
        }
        prompt.addView(message)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val btnLater = TextView(this).apply {
            setText(R.string.action_later)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            setTextColor(adaptiveColor)
            setOnClickListener { mainLayout?.removeView(prompt) }
        }
        buttons.addView(btnLater)

        val btnSet = TextView(this).apply {
            setText(R.string.action_set_default)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            setTextColor(getColor(android.R.color.holo_blue_dark))
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                mainLayout?.removeView(prompt)
                startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
            }
        }
        buttons.addView(btnSet)

        prompt.addView(buttons)

        val lp = FrameLayout.LayoutParams(dpToPx(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        mainLayout?.addView(prompt, lp)

        preferences.lastDefaultPromptTimestamp = System.currentTimeMillis()
        preferences.incrementDefaultPromptCount()
    }

    override fun saveHomeState() {
        debounceHandler.removeCallbacks(saveStateRunnable)
        debounceHandler.postDelayed(saveStateRunnable, 500)
    }

    private fun saveHomeStateInternal() {
        if (isStateRestored && homeItems.isNotEmpty()) {
            viewModel.saveHomeItems(homeItems)
        }
    }

    fun savePage(index: Int) {
        saveHomeState()
    }

    override fun isTransforming(): Boolean = freeformInteraction.isTransforming()

    override fun isFolderOpen(): Boolean = folderManager.isFolderOpen()

    fun getModel(): AppLoader? = appLoader

    private fun restoreHomeState() {
        homeItems = preferences.getHomeItems()
        homeView?.setHomeItems(homeItems)
        if (homeItems.isEmpty()) {
            setupDefaultHome()
        } else {
            for (item in homeItems) {
                renderHomeItem(item)
            }
        }
        isStateRestored = true
    }

    private fun setupDefaultHome() {
        saveHomeState()
    }

    fun setOverlayBlur(enabled: Boolean, isTransform: Boolean = false) {
        if (!preferences.isLiquidGlass) return
        if (!isTransform) {
            homeView?.let { ThemeUtils.applyBlurIfSupported(it, enabled) }
        } else {
            homeView?.let { ThemeUtils.applyBlurIfSupported(it, false) }
        }
        ThemeUtils.applyWindowBlur(window, enabled)
    }

    fun renderHomeItem(item: HomeItem?) {
        if (item?.type == null) return
        val view: View? = when (item.type) {
            HomeItem.Type.APP -> createAppView(item, false)
            HomeItem.Type.FOLDER -> createFolderView(item, false)
            HomeItem.Type.WIDGET -> createWidgetView(item)
            HomeItem.Type.CLOCK -> createClockView(item)
            else -> null
        }
        if (view != null) {
            homeView?.addItemView(item, view)
        }
    }

    fun createAppView(item: HomeItem, isOnGlass: Boolean): View? {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val scale = preferences.iconScale
        val size = (baseSize * scale).toInt()

        val iconView = ImageView(this).apply {
            tag = "item_icon"
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        val labelView = TextView(this).apply {
            tag = "item_label"
            setTextColor(ThemeUtils.getAdaptiveColor(this@MainActivity, preferences, isOnGlass))
            textSize = 10 * scale
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val app = findApp(item.packageName)
        if (app != null) {
            appLoader?.loadIcon(app, object : AppLoader.OnIconLoadedListener {
                override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                    iconView.setImageBitmap(icon)
                }
            })
            labelView.text = app.label
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            labelView.text = "..."
        }

        container.addView(iconView)
        container.addView(labelView)
        if (preferences.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    private fun createFolderView(item: HomeItem, isOnGlass: Boolean): View? {
        val hv = homeView
        val gridM = hv?.gridManager ?: gridManager
        val cellWidth = if ((hv?.width ?: 0) > 0) gridM.getCellWidth(hv!!.width) else 1
        val cellHeight = if ((hv?.height ?: 0) > 0) gridM.getCellHeight(hv!!.height) else 1

        val container = folderUI.createFolderView(item, isOnGlass, cellWidth, cellHeight)
        val grid = container.findViewWithTag<GridLayout>("folder_grid")
        if (grid != null) refreshFolderPreview(item, grid)

        return container
    }

    fun refreshFolderPreview(folder: HomeItem, grid: GridLayout) {
        grid.removeAllViews()
        val items = folder.folderItems ?: return
        if (items.isEmpty()) return

        val hv = homeView
        val gridM = hv?.gridManager ?: gridManager
        val cellWidth = if ((hv?.width ?: 0) > 0) gridM.getCellWidth(hv!!.width) else 1
        val cellHeight = if ((hv?.height ?: 0) > 0) gridM.getCellHeight(hv!!.height) else 1

        val scale = preferences.iconScale
        val isSmall = folder.spanX <= 1.0f && folder.spanY <= 1.0f
        val folderW = if (isSmall) (resources.getDimensionPixelSize(R.dimen.grid_icon_size) * scale).toInt() else (cellWidth * folder.spanX).toInt()
        val folderH = if (isSmall) folderW else (cellHeight * folder.spanY).toInt()

        val padding = dpToPx(if (isSmall) 6 else 12)
        val availableW = folderW - 2 * padding
        val availableH = folderH - 2 * padding

        var columns = if (isSmall) 2 else Math.max(2, Math.round(folder.spanX))
        if (columns > 4) columns = 4

        val iconsToShow = if (isSmall) Math.min(items.size, 4) else Math.min(items.size, columns * columns)

        grid.columnCount = columns
        grid.rowCount = Math.ceil(iconsToShow.toDouble() / columns).toInt()

        var iconSize = Math.min(availableW / columns, if (grid.rowCount > 0) availableH / grid.rowCount else availableH)
        val iconMargin = dpToPx(if (isSmall) 1 else 4)
        iconSize -= 2 * iconMargin

        for (i in 0 until iconsToShow) {
            val sub = items[i]
            val iv = ImageView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = iconSize
                    height = iconSize
                    setMargins(iconMargin, iconMargin, iconMargin, iconMargin)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            findApp(sub.packageName)?.let { app ->
                appLoader?.loadIcon(app, object : AppLoader.OnIconLoadedListener {
                    override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                        iv.setImageBitmap(icon)
                    }
                })
            }
            grid.addView(iv)
        }
    }

    fun handleAppLaunch(packageName: String?) {
        packageName ?: return
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) startActivity(intent)
        } catch (ignored: Exception) {
        }
    }

    fun mergeToFolder(target: HomeItem, dragged: HomeItem) = folderManager.mergeToFolder(target, dragged, homeItems)

    fun addToFolder(folder: HomeItem, dragged: HomeItem) = folderManager.addToFolder(folder, dragged, homeItems)

    private fun createWidgetView(item: HomeItem): View? {
        val am = appWidgetManager ?: return null
        val ah = appWidgetHost ?: return null
        val info = am.getAppWidgetInfo(item.widgetId) ?: return null
        return try {
            val hostView = ah.createView(this, item.widgetId, info) ?: return null
            hostView.setAppWidget(item.widgetId, info)
            val density = resources.displayMetrics.density
            val hv = homeView
            val grid = hv?.gridManager ?: gridManager
            val cellWidth = ((hv?.width ?: 0) - (hv?.paddingLeft ?: 0) - (hv?.paddingRight ?: 0)) / grid.columns
            val cellHeight = ((hv?.height ?: 0) - (hv?.paddingTop ?: 0) - (hv?.paddingBottom ?: 0)) / grid.rows
            if (cellWidth > 0 && cellHeight > 0) {
                val w = (cellWidth * item.spanX / density).toInt()
                val h = (cellHeight * item.spanY / density).toInt()
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
                }
                hostView.updateAppWidgetOptions(options)
            } else {
                hostView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                        val g = homeView?.gridManager ?: grid
                        val cw = ((homeView?.width ?: 0) - (homeView?.paddingLeft ?: 0) - (homeView?.paddingRight ?: 0)) / g.columns
                        val ch = ((homeView?.height ?: 0) - (homeView?.paddingTop ?: 0) - (homeView?.paddingBottom ?: 0)) / g.rows
                        if (cw > 0 && ch > 0) {
                            val w = (cw * item.spanX / density).toInt()
                            val h = (ch * item.spanY / density).toInt()
                            val options = Bundle().apply {
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
                            }
                            hostView.updateAppWidgetOptions(options)
                            hostView.removeOnLayoutChangeListener(this)
                        }
                    }
                })
            }
            hostView
        } catch (e: Exception) {
            null
        }
    }

    override fun removeHomeItem(item: HomeItem, v: View) {
        val page = item.page
        homeItems.remove(item)
        (v.parent as? ViewGroup)?.removeView(v)
        savePage(page)
        homeView?.pageManager?.cleanupEmptyPages()
        homeView?.refreshIcons(appLoader!!, allApps)
    }

    override fun removePackageItems(packageName: String) {
        var changed = false
        val iterator = homeItems.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.type == HomeItem.Type.APP && packageName == item.packageName) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            homeView?.removeItemsByPackage(packageName)
            homeView?.refreshIcons(appLoader!!, allApps)
            saveHomeState()
        }
    }

    override fun showAppInfo(item: HomeItem) {
        if (item.packageName.isNullOrEmpty()) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${item.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.app_info_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createClockView(item: HomeItem): View {
        val clockRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val tvTime = TextView(this).apply {
            textSize = 64f
            setTextColor(ThemeUtils.getAdaptiveColor(this@MainActivity, preferences, false))
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }

        val tvDate = TextView(this).apply {
            textSize = 18f
            val adaptiveDim = ThemeUtils.getAdaptiveColor(this@MainActivity, preferences, false) and 0xBBFFFFFF.toInt()
            setTextColor(adaptiveDim)
            gravity = Gravity.CENTER
        }

        clockRoot.addView(tvTime)
        clockRoot.addView(tvDate)

        val updateTask = object : Runnable {
            override fun run() {
                val cal = Calendar.getInstance()
                tvTime.text = android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(cal.time)
                tvDate.text = android.text.format.DateFormat.getMediumDateFormat(this@MainActivity).format(cal.time)
                tvTime.postDelayed(this, 10000)
            }
        }
        tvTime.post(updateTask)
        return clockRoot
    }

    private fun findApp(packageName: String?): AppItem? {
        return allApps.find { it.packageName == packageName }
    }

    override fun showHomeContextMenu(col: Float, row: Float, page: Int) {
        val menu = PageMenuUI(this, preferences, object : PageMenuUI.PageActionCallback {
            override fun onAddPage() {
                homeView?.addPage()
                saveHomeState()
                Toast.makeText(this@MainActivity, R.string.page_added, Toast.LENGTH_SHORT).show()
            }
            override fun onRemovePage() {
                homeView?.let {
                    if (it.getPageCount() > 1) {
                        it.removePage(it.getCurrentPage())
                        saveHomeState()
                    }
                }
            }
            override fun onOpenWidgets() {
                val c = if (!preferences.isFreeformHome) Math.round(col).toFloat() else col
                val r = if (!preferences.isFreeformHome) Math.round(row).toFloat() else row
                widgetManager.pickWidget(c, r)
            }
            override fun onOpenWallpaper() = openWallpaperPicker()
            override fun onOpenSettings() = openSettings()
            override fun getPageCount(): Int = homeView?.getPageCount() ?: 0
        })
        menu.showPageMenu()
    }

    private fun openWallpaperPicker() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
                startActivity(Intent.createChooser(pickIntent, getString(R.string.title_select_wallpaper)))
            } catch (e2: Exception) {
                Toast.makeText(this, getString(R.string.wallpaper_picker_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSettings() {
        try {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Launcher settings could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyDynamicColors() {
        ThemeMechanism.getSystemAccentColor(this)?.let { accentColor ->
            homeView?.setAccentColor(accentColor)
            drawerView?.setAccentColor(accentColor)
        }
    }

    override fun loadApps() {
        appLoader?.loadApps(object : AppLoader.OnAppsLoadedListener {
            override fun onAppsLoaded(apps: List<AppItem>) {
                allApps = apps.toMutableList()
                drawerView?.setApps(allApps, appLoader)
                homeView?.refreshIcons(appLoader!!, allApps)
            }
        })
    }

    private fun registerAppInstallReceiver() {
        appInstallReceiver = AppInstallReceiver(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appInstallReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(appInstallReceiver, filter)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        appLoader?.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            allApps.clear()
            System.gc()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainLayout?.closeDrawer()
        homeView?.scrollToPage(0)
    }


    private fun updateHomeItemFromTransform(v: View) {
        val item = v.tag as HomeItem
        val isFreeform = preferences.isFreeformHome

        val parent = homeView?.let {
            item.page = it.getCurrentPage()
            val pagesContainer = it.pagesContainer
            if (item.page < pagesContainer.childCount) {
                pagesContainer.getChildAt(item.page) as? ViewGroup
            } else null
        } ?: return

        val cellWidth = gridManager.getCellWidth(parent.width)
        val cellHeight = gridManager.getCellHeight(parent.height)

        if (isFreeform) {
            item.rotation = v.rotation
            item.scaleX = v.scaleX
            item.scaleY = v.scaleY
            item.tiltX = v.rotationX
            item.tiltY = v.rotationY
        } else {
            item.rotation = 0f
            item.scaleX = 1.0f
            item.scaleY = 1.0f
            item.tiltX = 0f
            item.tiltY = 0f
            if ((item.type == HomeItem.Type.WIDGET || item.type == HomeItem.Type.FOLDER) && cellWidth > 0 && cellHeight > 0) {
                item.spanX = Math.round(v.width.toFloat() / cellWidth).toFloat()
                item.spanY = Math.round(v.height.toFloat() / cellHeight).toFloat()
                if (item.spanX < 1) item.spanX = 1f
                if (item.spanY < 1) item.spanY = 1f
            }
        }

        val pagePos = IntArray(2)
        parent.getLocationOnScreen(pagePos)
        val rootPos = IntArray(2)
        mainLayout?.getLocationOnScreen(rootPos)

        val xInParent = v.x - (pagePos[0] - rootPos[0])
        val yInParent = v.y - (pagePos[1] - rootPos[1])

        if (cellWidth > 0) item.col = if (isFreeform) xInParent / cellWidth else Math.round(xInParent / cellWidth).toFloat()
        if (cellHeight > 0) item.row = if (isFreeform) yInParent / cellHeight else Math.round(yInParent / cellHeight).toFloat()

        if (!isFreeform && homeView != null) {
            val hv = homeView!!
            if (hv.isAreaOccupied(item.col.toInt(), item.row.toInt(), Math.round(item.spanX), Math.round(item.spanY), item.page, item)) {
                val spot = hv.findNearestEmptySpot(item, item.page)
                if (spot != null) {
                    item.col = spot.first.toFloat()
                    item.row = spot.second.toFloat()
                }
            }
        }

        val centerX = v.x + v.width / 2f
        val centerY = v.y + v.height / 2f
        val collisionView = findHomeItemAtRoot(centerX, centerY, v)
        if (collisionView != null && item.type == HomeItem.Type.APP) {
            val target = collisionView.tag as? HomeItem
            if (target != null) {
                if (target.type == HomeItem.Type.APP) {
                    mainLayout?.removeView(v)
                    mergeToFolder(target, item)
                    return
                } else if (target.type == HomeItem.Type.FOLDER) {
                    mainLayout?.removeView(v)
                    addToFolder(target, item)
                    return
                }
            }
        }
    }

    override fun showTransformOverlay(v: View) {
        setOverlayBlur(true, true)
        freeformInteraction.showTransformOverlay(v)
    }

    override fun startTransformDirectMove(v: View, x: Float, y: Float) {
        freeformInteraction.startDirectMove(x, y)
    }

    private fun findHomeItemAtRoot(x: Float, y: Float, exclude: View): View? {
        val hv = homeView ?: return null
        val page = hv.getCurrentPage()
        val pagesContainer = hv.pagesContainer
        if (page < pagesContainer.childCount) {
            val pageLayout = pagesContainer.getChildAt(page) as? ViewGroup ?: return null
            val pagePos = IntArray(2)
            pageLayout.getLocationOnScreen(pagePos)
            val rootPos = IntArray(2)
            mainLayout?.getLocationOnScreen(rootPos)
            val adjX = x - (pagePos[0] - rootPos[0])
            val adjY = y - (pagePos[1] - rootPos[1])
            for (i in pageLayout.childCount - 1 downTo 0) {
                val child = pageLayout.getChildAt(i)
                if (child === exclude) continue
                if (hv.getVisualRect(child).contains(adjX, adjY)) return child
            }
        }
        return null
    }

    private fun closeTransformOverlay() {
        freeformInteraction.closeTransformOverlay()
        setOverlayBlur(false, true)
        homeView?.pageManager?.cleanupEmptyPages()
        homeView?.refreshIcons(appLoader!!, allApps)
    }

    override fun onResume() {
        super.onResume()
        ThemeUtils.updateStatusBarContrast(this)
        autoDimming.updateDimVisibility()
        homeView?.refreshLayout()
        homeView?.refreshIcons(appLoader!!, allApps)
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost?.startListening()
        if (allApps.isEmpty()) loadApps()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost?.stopListening()
        allApps.clear()
        debounceHandler.removeCallbacksAndMessages(null)
        System.gc()
    }

    override fun onDestroy() {
        super.onDestroy()
        appInstallReceiver?.let { unregisterReceiver(it) }
        appLoader = null
        mainLayout = null
        homeView = null
        drawerView = null
        appInstallReceiver = null
    }

    private fun configureWidget(appWidgetId: Int, lastGridCol: Float, lastGridRow: Float, spanX: Float, spanY: Float) {
        val info = appWidgetManager?.getAppWidgetInfo(appWidgetId) ?: return
        if (info.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("lastGridCol", lastGridCol)
                putExtra("lastGridRow", lastGridRow)
                putExtra("spanX", spanX)
                putExtra("spanY", spanY)
                putExtra("page", pendingWidgetPage)
            }
            widgetConfigLauncher.launch(intent)
        } else createWidgetAt(appWidgetId, lastGridCol, lastGridRow, spanX, spanY, pendingWidgetPage)
    }

    fun createWidgetAt(appWidgetId: Int, col: Float, row: Float, spanX: Float, spanY: Float, page: Int = -1) {
        val targetPage = if (page >= 0) page else (homeView?.getCurrentPage() ?: 0)
        val item = HomeItem.createWidget(appWidgetId, col, row, spanX, spanY, targetPage)
        onExternalItemDropped(item)
    }

    fun onExternalItemDropped(item: HomeItem) {
        Logger.d("External item dropped: ${item.packageName}")
        homeItems.add(item)
        renderHomeItem(item)
        saveHomeState()
    }

    fun setPendingWidgetParams(col: Float, row: Float, spanX: Float, spanY: Float, page: Int) {
        pendingWidgetCol = col
        pendingWidgetRow = row
        pendingWidgetSpanX = spanX
        pendingWidgetSpanY = spanY
        pendingWidgetPage = page
    }

    fun startNewWidgetDrag(info: android.appwidget.AppWidgetProviderInfo, spanX: Float, spanY: Float) {
        val am = appWidgetManager ?: return
        val ah = appWidgetHost ?: return
        val appWidgetId = ah.allocateAppWidgetId()
        val currentPage = homeView?.getCurrentPage() ?: 0

        val bound = am.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        val item = HomeItem.createWidget(appWidgetId, 0f, 0f, spanX, spanY, currentPage)
        item.packageName = info.provider.packageName
        item.className = info.provider.className

        val view = if (bound) {
            createWidgetView(item)
        } else {
            createWidgetPlaceholderView(info, spanX, spanY)
        }

        if (view != null) {
            view.tag = item

            val hv = homeView
            val grid = hv?.gridManager ?: gridManager
            val availW = if (hv != null && hv.width > 0) hv.width - hv.paddingLeft - hv.paddingRight else resources.displayMetrics.widthPixels
            val availH = if (hv != null && hv.height > 0) hv.height - hv.paddingTop - hv.paddingBottom else resources.displayMetrics.heightPixels
            val cellWidth = grid.getCellWidth(availW)
            val cellHeight = grid.getCellHeight(availH)

            val w = (cellWidth * spanX).toInt()
            val h = (cellHeight * spanY).toInt()

            mainLayout?.addView(view, FrameLayout.LayoutParams(w, h))
            mainLayout?.startExternalDrag(view)
        }
    }

    private fun createWidgetPlaceholderView(info: android.appwidget.AppWidgetProviderInfo, spanX: Float, spanY: Float): View {
        val frame = FrameLayout(this)
        frame.background = ThemeUtils.getGlassDrawable(this, preferences, 12f)
        val iv = ImageView(this)
        try {
            iv.setImageDrawable(info.loadPreviewImage(this, 0) ?: info.loadIcon(this, 0))
        } catch (e: Exception) {
            iv.setImageResource(R.drawable.ic_widgets)
        }
        frame.addView(iv, FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.CENTER))

        val hv = homeView
        val grid = hv?.gridManager ?: gridManager
        val availW = if (hv != null && hv.width > 0) hv.width - hv.paddingLeft - hv.paddingRight else resources.displayMetrics.widthPixels
        val availH = if (hv != null && hv.height > 0) hv.height - hv.paddingTop - hv.paddingBottom else resources.displayMetrics.heightPixels
        val cellWidth = grid.getCellWidth(availW)
        val cellHeight = grid.getCellHeight(availH)

        frame.layoutParams = ViewGroup.LayoutParams(
            (cellWidth * spanX).toInt().coerceAtLeast(dpToPx(100)),
            (cellHeight * spanY).toInt().coerceAtLeast(dpToPx(100))
        )
        return frame
    }

    fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) { packageName }
    }

    private fun uninstallApp(item: HomeItem) {
        if (item.packageName.isNullOrEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = "package:${item.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.uninstall_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyThemeMode(mode: String?) {
        ThemeMechanism.applyThemeMode(this, mode)
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    override fun getHomeView(): HomeView? = homeView
    override fun getDrawerView(): DrawerView? = drawerView
    override fun getSettingsManager(): LauncherPreferences = preferences
    override fun getCurrentPage(): Int = homeView?.getCurrentPage() ?: 0
    override fun scrollToPage(page: Int) { homeView?.scrollToPage(page) }

    override fun getAppLoader(): AppLoader? = appLoader

    override fun handleItemClick(v: View) {
        val item = v.tag as? HomeItem ?: return
        when (item.type) {
            HomeItem.Type.APP -> {
                handleAppLaunch(item.packageName)
            }
            HomeItem.Type.FOLDER -> folderManager.openFolder(item, v, homeItems, allApps, appLoader)
            HomeItem.Type.WIDGET -> widgetManager.showWidgetOptions(item, v)
            else -> {}
        }
    }

    override fun openDrawer() {
        mainLayout?.openDrawer()
    }

    override fun closeDrawer() {
        mainLayout?.closeDrawer()
    }

    override fun closeDrawerInstantly() {
        mainLayout?.closeDrawerInstantly()
    }

    override fun getTransformOverlay(): View? = freeformInteraction.getOverlay()
}
