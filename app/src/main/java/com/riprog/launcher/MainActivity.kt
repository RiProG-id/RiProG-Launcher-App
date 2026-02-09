package com.riprog.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.UiModeManager
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
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

class MainActivity : Activity() {

    private lateinit var model: LauncherModel
    private lateinit var settingsManager: SettingsManager
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var mainLayout: MainLayout
    private lateinit var homeView: HomeView
    private lateinit var drawerView: DrawerView
    private var appInstallReceiver: AppInstallReceiver? = null
    private var homeItems: MutableList<HomeItem> = ArrayList()
    private var allApps: List<AppItem> = ArrayList()
    private var lastGridCol = 0f
    private var lastGridRow = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        applyThemeMode(settingsManager.themeMode)

        val w = window
        @Suppress("DEPRECATION")
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        @Suppress("DEPRECATION")
        w.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        w.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            w.setDecorFitsSystemWindows(false)
        }

        model = LauncherModel(this)

        mainLayout = MainLayout(this)
        homeView = HomeView(this)
        drawerView = DrawerView(this)
        drawerView.setColumns(settingsManager.columns)
        drawerView.setOnAppLongClickListener { app ->
            mainLayout.closeDrawer()
            val item = HomeItem.createApp(app.packageName, app.className, 0f, 0f, homeView.getCurrentPage())
            homeItems.add(item)
            val view = createAppView(item)
            homeView.addItemView(item, view)
            saveHomeState()
            mainLayout.startExternalDrag(view)
        }

        mainLayout.addView(homeView)
        mainLayout.addView(drawerView)
        drawerView.visibility = View.GONE

        setContentView(mainLayout)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetHost.startListening()

        applyDynamicColors()
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
            ?: return false
        return packageName == resolveInfo.activityInfo.packageName
    }

    private fun showDefaultLauncherPrompt() {
        if (isDefaultLauncher()) return

        val lastShown = settingsManager.lastDefaultPromptTimestamp
        val count = settingsManager.defaultPromptCount

        // Show every 24 hours, max 5 times
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
            val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
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

    private fun renderHomeItem(item: HomeItem) {
        val view: View? = when (item.type) {
            HomeItem.Type.APP -> createAppView(item)
            HomeItem.Type.WIDGET -> createWidgetView(item)
            HomeItem.Type.CLOCK -> createClockView(item)
            else -> null
        }
        if (view != null) {
            homeView.addItemView(item, view)
        }
    }

    private fun createAppView(item: HomeItem): View {
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
        labelView.textSize = 10 * scale
        labelView.gravity = Gravity.CENTER
        labelView.maxLines = 1
        labelView.ellipsize = TextUtils.TruncateAt.END

        val app = findApp(item.packageName)
        if (app != null) {
            model.loadIcon(app) { iconView.setImageBitmap(it) }
            labelView.text = app.label
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            labelView.text = "..."
        }

        container.addView(iconView)
        container.addView(labelView)
        return container
    }

    private fun createWidgetView(item: HomeItem): View? {
        val info = appWidgetManager.getAppWidgetInfo(item.widgetId) ?: return null
        val hostView = appWidgetHost.createView(this, item.widgetId, info)
        hostView.setAppWidget(item.widgetId, info)
        return hostView
    }

    private fun showWidgetOptions(item: HomeItem, hostView: View) {
        val options = arrayOf(getString(R.string.action_resize), getString(R.string.action_remove))
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(options) { _, which ->
                if (which == 0) showResizeDialog(item, hostView)
                else removeHomeItem(item, hostView)
            }.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.glass_bg)
    }

    private fun showResizeDialog(item: HomeItem, hostView: View) {
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
                homeView.updateViewPosition(item, hostView)
                saveHomeState()
            }.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.glass_bg)
    }

    private fun removeHomeItem(item: HomeItem, view: View?) {
        homeItems.remove(item)
        if (view != null && view.parent is ViewGroup) {
            (view.parent as ViewGroup).removeView(view)
        }
        saveHomeState()
        homeView.refreshIcons(model, allApps)
    }

    private fun showAppInfo(item: HomeItem?) {
        if (item?.packageName == null || item.packageName!!.isEmpty()) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:" + item.packageName)
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
        tvTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL))

        val tvDate = TextView(this)
        tvDate.textSize = 18f
        tvDate.setTextColor(getColor(R.color.foreground_dim))
        tvDate.gravity = Gravity.CENTER

        clockRoot.addView(tvTime)
        clockRoot.addView(tvDate)

        val updateTask = object : Runnable {
            override fun run() {
                val cal = java.util.Calendar.getInstance()
                tvTime.text = DateFormat.getTimeFormat(this@MainActivity).format(cal.time)
                tvDate.text = DateFormat.getMediumDateFormat(this@MainActivity).format(cal.time)
                tvTime.postDelayed(this, 10000)
            }
        }
        tvTime.post(updateTask)
        return clockRoot
    }

    private fun findApp(packageName: String?): AppItem? {
        if (packageName == null) return null
        return allApps.find { it.packageName == packageName }
    }

    private fun showHomeContextMenu(col: Float, row: Float, page: Int) {
        val options = arrayOf(
            getString(R.string.menu_widgets),
            getString(R.string.menu_wallpaper),
            getString(R.string.menu_settings),
            getString(R.string.menu_layout)
        )
        val icons = intArrayOf(R.drawable.ic_widgets, R.drawable.ic_wallpaper, R.drawable.ic_settings, R.drawable.ic_layout)

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.select_dialog_item, android.R.id.text1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<TextView>(android.R.id.text1)
                tv.setCompoundDrawablesWithIntrinsicBounds(icons[position], 0, 0, 0)
                tv.compoundDrawablePadding = dpToPx(16)
                tv.setTextColor(getColor(R.color.foreground))
                val d = tv.compoundDrawables[0]
                d?.setTint(getColor(R.color.foreground))
                return view
            }
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_home_menu)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> {
                        lastGridCol = col
                        lastGridRow = row
                        pickWidget()
                    }
                    1 -> openWallpaperPicker()
                    2 -> openSettings()
                    3 -> showLayoutOptions()
                }
            }.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.glass_bg)
    }

    private fun pickAppForHome(col: Int, row: Int, page: Int) {
        if (allApps.isEmpty()) {
            Toast.makeText(this, getString(R.string.apps_not_loaded), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = allApps.map { it.label }.toTypedArray()

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_pick_app)
            .setItems(labels) { _, which ->
                val selected = allApps[which]
                val item = HomeItem.createApp(selected.packageName, selected.className, col.toFloat(), row.toFloat(), page)
                homeItems.add(item)
                renderHomeItem(item)
                saveHomeState()
            }.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.glass_bg)
    }

    private fun openWallpaperPicker() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback to gallery/internal chooser
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

    private fun showLayoutOptions() {
        val options = arrayOf(getString(R.string.layout_add_page), getString(R.string.layout_remove_page))
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.menu_layout)
            .setItems(options) { _, which ->
                if (which == 0) {
                    homeView.addPage()
                    Toast.makeText(this, getString(R.string.page_added), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.remove_page_not_implemented), Toast.LENGTH_SHORT).show()
                }
            }.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.glass_bg)
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
            registerReceiver(appInstallReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(appInstallReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        homeView.refreshLayout()
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
        appInstallReceiver?.let { unregisterReceiver(it) }
        model.shutdown()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            loadApps()
            homeView.refreshLayout()
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
        val item = HomeItem.createWidget(appWidgetId, lastGridCol, lastGridRow, 2, 1, homeView.getCurrentPage())
        homeItems.add(item)
        renderHomeItem(item)
        saveHomeState()
    }

    fun pickWidget() {
        val providers = appWidgetManager.installedProviders
        val grouped = HashMap<String, MutableList<AppWidgetProviderInfo>>()
        for (info in providers) {
            val pkg = info.provider.packageName
            if (!grouped.containsKey(pkg)) grouped[pkg] = ArrayList()
            grouped[pkg]!!.add(info)
        }

        val scrollView = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        scrollView.addView(root)

        val packages = grouped.keys.toMutableList()
        packages.sortWith { a, b ->
            val labelA = getAppName(a)
            val labelB = getAppName(b)
            labelA.compareTo(labelB, ignoreCase = true)
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_pick_widget)
            .setView(scrollView)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        for (pkg in packages) {
            val header = TextView(this)
            header.text = getAppName(pkg)
            header.textSize = 18f
            header.setTypeface(null, Typeface.BOLD)
            header.setTextColor(getColor(R.color.foreground))
            header.setPadding(0, dpToPx(16), 0, dpToPx(8))
            root.addView(header)

            for (info in grouped[pkg]!!) {
                val item = LinearLayout(this)
                item.orientation = LinearLayout.HORIZONTAL
                item.gravity = Gravity.CENTER_VERTICAL
                item.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                item.isClickable = true
                item.setBackgroundResource(android.R.drawable.list_selector_background)

                // Visual preview
                val preview = ImageView(this)
                val spanX = Math.max(1, info.minWidth / (resources.displayMetrics.widthPixels / HomeView.GRID_COLUMNS))
                val spanY = Math.max(1, info.minHeight / (resources.displayMetrics.heightPixels / HomeView.GRID_ROWS))

                var previewDrawable = info.loadPreviewImage(this, 0)
                if (previewDrawable == null) {
                    previewDrawable = info.loadIcon(this, 0)
                }
                preview.setImageDrawable(previewDrawable)
                preview.scaleType = ImageView.ScaleType.FIT_CENTER

                val shape = GradientDrawable()
                shape.setColor(getColor(R.color.search_background))
                shape.cornerRadius = dpToPx(4).toFloat()
                shape.setStroke(dpToPx(1), getColor(R.color.foreground_dim))
                preview.background = shape

                val previewParams = LinearLayout.LayoutParams(dpToPx(60), dpToPx(60))
                previewParams.rightMargin = dpToPx(12)
                item.addView(preview, previewParams)

                val textLayout = LinearLayout(this)
                textLayout.orientation = LinearLayout.VERTICAL

                val label = TextView(this)
                label.text = info.loadLabel(packageManager)
                label.setTextColor(getColor(R.color.foreground))
                textLayout.addView(label)

                val size = TextView(this)
                size.text = getString(R.string.widget_size_format, spanX, spanY)
                size.textSize = 12f
                size.setTextColor(getColor(R.color.foreground_dim))
                textLayout.addView(size)

                item.addView(textLayout)

                item.setOnClickListener {
                    dialog.dismiss()
                    val appWidgetId = appWidgetHost.allocateAppWidgetId()
                    val allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
                    if (allowed) {
                        val homeItem = HomeItem.createWidget(appWidgetId, lastGridCol, lastGridRow, spanX, spanY, homeView.getCurrentPage())
                        homeItems.add(homeItem)
                        renderHomeItem(homeItem)
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
        dialog.window?.setBackgroundDrawableResource(R.drawable.glass_bg)
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun applyThemeMode(mode: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            var nightMode = UiModeManager.MODE_NIGHT_AUTO
            if ("light" == mode) nightMode = UiModeManager.MODE_NIGHT_NO else if ("dark" == mode) nightMode = UiModeManager.MODE_NIGHT_YES

            if (uiModeManager.nightMode != nightMode) {
                uiModeManager.setApplicationNightMode(nightMode)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private inner class AppInstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
    }

    private inner class MainLayout(context: Context) : FrameLayout(context) {
        private var isDrawerOpen = false
        private var startX = 0f
        private var startY = 0f
        private var downTime: Long = 0
        private var isGestureCanceled = false
        private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
        private val longPressHandler = Handler(Looper.getMainLooper())
        private var touchedView: View? = null
        private var longPressTriggered = false
        private var isDragging = false
        private var dragOverlay: LinearLayout? = null
        private lateinit var ivRemove: ImageView
        private lateinit var ivAppInfo: ImageView
        private var origCol = 0f
        private var origRow = 0f
        private var origPage = 0
        private var isExternalDrag = false

        private val longPressRunnable = Runnable {
            longPressTriggered = true
            if (touchedView != null) {
                isDragging = true
                isExternalDrag = false
                val item = touchedView!!.tag as? HomeItem
                if (item != null) {
                    origCol = item.col
                    origRow = item.row
                    origPage = item.page
                }
                dragOverlay?.let {
                    val isApp = item != null && item.type == HomeItem.Type.APP
                    ivAppInfo.visibility = if (isApp) View.VISIBLE else View.GONE
                    it.visibility = View.VISIBLE
                }
                homeView.startDragging(touchedView!!, startX, startY)
            } else {
                val cellWidth = width / HomeView.GRID_COLUMNS
                val cellHeight = height / HomeView.GRID_ROWS
                val col = startX / if (cellWidth > 0) cellWidth.toFloat() else 1.0f
                val row = startY / if (cellHeight > 0) cellHeight.toFloat() else 1.0f
                showHomeContextMenu(col, row, homeView.getCurrentPage())
            }
        }

        init {
            setupDragOverlay()
        }

        override fun performClick(): Boolean {
            return super.performClick()
        }

        private fun setupDragOverlay() {
            val overlay = LinearLayout(context)
            overlay.orientation = LinearLayout.HORIZONTAL
            overlay.setBackgroundResource(R.drawable.glass_bg)
            overlay.gravity = Gravity.CENTER
            overlay.visibility = View.GONE
            overlay.elevation = dpToPx(8).toFloat()

            ivRemove = ImageView(context)
            ivRemove.setImageResource(R.drawable.ic_remove)
            ivRemove.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            ivRemove.contentDescription = context.getString(R.string.drag_remove)
            overlay.addView(ivRemove)

            ivAppInfo = ImageView(context)
            ivAppInfo.setImageResource(R.drawable.ic_info)
            ivAppInfo.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            ivAppInfo.contentDescription = context.getString(R.string.drag_app_info)
            overlay.addView(ivAppInfo)

            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            lp.topMargin = dpToPx(48)
            addView(overlay, lp)
            dragOverlay = overlay
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (isDrawerOpen) {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = ev.x
                        startY = ev.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = ev.y - startY
                        val dx = ev.x - startX
                        // Swipe down to close (more sensitive if at top)
                        if (dy > touchSlop && dy > Math.abs(dx)) {
                            if (drawerView.isAtTop() || dy > touchSlop * 4) {
                                return true
                            }
                        }
                    }
                }
                return false
            }

            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    startY = ev.y
                    downTime = System.currentTimeMillis()
                    isGestureCanceled = false
                    longPressTriggered = false
                    isDragging = false
                    touchedView = findTouchedHomeItem(startX, startY)
                    longPressHandler.removeCallbacks(longPressRunnable)
                    longPressHandler.postDelayed(longPressRunnable, 400)
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    // Explicitly intercept upward swipe for drawer
                    if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        return true
                    }
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        if (!longPressTriggered) {
                            return true // Intercept for other swipes
                        }
                    }
                    return isDragging
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) return true
                    val duration = System.currentTimeMillis() - downTime
                    if (duration < 80) { // Accidental touch/debounce
                        longPressHandler.removeCallbacks(longPressRunnable)
                        return false
                    }
                }
            }
            return isDragging
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isDrawerOpen) {
                // Handle swipe down to close drawer
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startX = event.x
                    startY = event.y
                } else if (event.action == MotionEvent.ACTION_MOVE) {
                    val dy = event.y - startY
                    if (dy > touchSlop) {
                        closeDrawer()
                        return true
                    }
                } else if (event.action == MotionEvent.ACTION_UP) {
                    val dy = event.y - startY
                    if (dy > touchSlop) closeDrawer()
                }
                return true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - startX
                    val dy = event.y - startY

                    if (isDragging) {
                        homeView.handleDrag(event.x, event.y)
                        updateDragHighlight(event.x, event.y)
                        return true
                    }

                    if (!isGestureCanceled && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        if (Math.abs(dy) > Math.abs(dx)) {
                            if (dy < -touchSlop * 2) {
                                openDrawer()
                                isGestureCanceled = true
                            }
                        } else {
                            if (dx > touchSlop * 2 && homeView.getCurrentPage() > 0) {
                                homeView.scrollToPage(homeView.getCurrentPage() - 1)
                                isGestureCanceled = true
                            } else if (dx < -touchSlop * 2 && homeView.getCurrentPage() < homeView.getPageCount() - 1) {
                                homeView.scrollToPage(homeView.getCurrentPage() + 1)
                                isGestureCanceled = true
                            }
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (isDragging) {
                        dragOverlay?.let {
                            val overlayHeight = it.height
                            val overlayWidth = it.width
                            val left = (width - overlayWidth) / 2f
                            it.visibility = View.GONE
                            ivRemove.setBackgroundColor(Color.TRANSPARENT)
                            ivAppInfo.setBackgroundColor(Color.TRANSPARENT)

                            if (event.y < it.bottom + touchSlop * 2 &&
                                event.x >= left && event.x <= left + overlayWidth) {
                                val item = touchedView?.tag as? HomeItem
                                if (item != null) {
                                    val isApp = ivAppInfo.visibility == View.VISIBLE
                                    if (!isApp) {
                                        removeHomeItem(item, touchedView)
                                    } else {
                                        val x = event.x
                                        if (x < left + overlayWidth / 2f) {
                                            removeHomeItem(item, touchedView)
                                        } else {
                                            showAppInfo(item)
                                            revertPosition(item, touchedView!!)
                                        }
                                    }
                                }
                                homeView.cancelDragging()
                            } else {
                                homeView.endDragging()
                            }
                        } ?: homeView.endDragging()
                        isDragging = false
                        return true
                    }
                    if (!isGestureCanceled && !longPressTriggered) {
                        val duration = System.currentTimeMillis() - downTime
                        val finalDx = event.x - startX
                        val finalDy = event.y - startY
                        val dist = Math.sqrt((finalDx * finalDx + finalDy * finalDy).toDouble()).toFloat()
                        if (duration >= 80 && duration < 150 && dist < touchSlop) {
                            touchedView?.let { handleItemClick(it) } ?: performClick()
                        }
                    }
                    return true
                }
            }
            return true
        }

        private fun findTouchedHomeItem(x: Float, y: Float): View? {
            val page = homeView.getCurrentPage()
            val pagesContainer = homeView.getChildAt(0) as? ViewGroup
            if (pagesContainer != null && page < pagesContainer.childCount) {
                val pageLayout = pagesContainer.getChildAt(page) as? ViewGroup
                if (pageLayout != null) {
                    val adjustedX = x - pagesContainer.paddingLeft
                    val adjustedY = y - pagesContainer.paddingTop
                    for (i in pageLayout.childCount - 1 downTo 0) {
                        val child = pageLayout.getChildAt(i)
                        if (adjustedX >= child.x && adjustedX <= child.x + child.width &&
                            adjustedY >= child.y && adjustedY <= child.y + child.height) {
                            return child
                        }
                    }
                }
            }
            return null
        }

        private fun handleItemClick(v: View) {
            val item = v.tag as? HomeItem ?: return
            if (item.type == HomeItem.Type.APP) {
                val intent = packageManager.getLaunchIntentForPackage(item.packageName!!)
                if (intent != null) startActivity(intent)
            } else if (item.type == HomeItem.Type.WIDGET) {
                showWidgetOptions(item, v)
            }
        }

        fun openDrawer() {
            if (isDrawerOpen) return
            isDrawerOpen = true
            settingsManager.incrementDrawerOpenCount()
            drawerView.visibility = View.VISIBLE
            drawerView.alpha = 0f
            drawerView.translationY = height / 4f
            drawerView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            homeView.animate().alpha(0f).setDuration(250).start()
            drawerView.onOpen()
        }

        fun startExternalDrag(v: View) {
            isDragging = true
            isExternalDrag = true
            dragOverlay?.let {
                val item = v.tag as? HomeItem
                val isApp = item != null && item.type == HomeItem.Type.APP
                ivAppInfo.visibility = if (isApp) View.VISIBLE else View.GONE
                it.visibility = View.VISIBLE
            }
            touchedView = v

            val iconSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            v.x = startX - iconSize / 2f
            v.y = startY - iconSize / 2f - dpToPx(48)

            homeView.startDragging(v, startX, startY)
        }

        private fun updateDragHighlight(x: Float, y: Float) {
            val overlay = dragOverlay ?: return
            if (overlay.visibility != View.VISIBLE) return

            val overlayHeight = overlay.height
            val overlayWidth = overlay.width
            val left = (width - overlayWidth) / 2f
            val isApp = ivAppInfo.visibility == View.VISIBLE

            ivRemove.setBackgroundColor(Color.TRANSPARENT)
            ivAppInfo.setBackgroundColor(Color.TRANSPARENT)

            if (y < overlay.bottom + touchSlop * 2 && x >= left && x <= left + overlayWidth) {
                if (!isApp) {
                    ivRemove.setBackgroundColor(0x40FFFFFF)
                } else {
                    if (x < left + overlayWidth / 2f) {
                        ivRemove.setBackgroundColor(0x40FFFFFF)
                    } else {
                        ivAppInfo.setBackgroundColor(0x40FFFFFF)
                    }
                }
            }
        }

        private fun revertPosition(item: HomeItem, v: View) {
            if (isExternalDrag) {
                removeHomeItem(item, v)
            } else {
                item.col = origCol
                item.row = origRow
                item.page = origPage
                homeView.addItemView(item, v)
                homeView.updateViewPosition(item, v)
                saveHomeState()
            }
        }

        fun closeDrawer() {
            if (!isDrawerOpen) return
            isDrawerOpen = false
            drawerView.animate()
                .translationY(height / 4f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    drawerView.visibility = View.GONE
                    homeView.visibility = View.VISIBLE
                }
                .start()
            homeView.visibility = View.VISIBLE
            homeView.animate().alpha(1f).setDuration(200).start()
            val imm = getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    companion object {
        private const val REQUEST_PICK_APPWIDGET = 1
        private const val REQUEST_CREATE_APPWIDGET = 2
        private const val APPWIDGET_HOST_ID = 1024
    }
}
