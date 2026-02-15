package com.riprog.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.UiModeManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.riprog.launcher.model.AppItem
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.model.LauncherModel
import com.riprog.launcher.receiver.AppInstallReceiver
import com.riprog.launcher.ui.*
import com.riprog.launcher.utils.SettingsManager
import com.riprog.launcher.utils.ThemeUtils
import java.util.*
import java.util.concurrent.Executors

class MainActivity : Activity(), MainLayout.Callback, AppInstallReceiver.Callback {

    companion object {
        private const val REQUEST_PICK_APPWIDGET = 1
        private const val REQUEST_CREATE_APPWIDGET = 2
        private const val APPWIDGET_HOST_ID = 1024
        private val widgetPreviewExecutor = Executors.newFixedThreadPool(4)
    }

    private var model: LauncherModel? = null
    private lateinit var settingsManager: SettingsManager
    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null
    private var mainLayout: MainLayout? = null
    private var homeView: HomeView? = null
    private var drawerView: DrawerView? = null
    private var currentFolderOverlay: View? = null
    private var currentTransformOverlay: TransformOverlay? = null
    private var transformingViewOriginalParent: ViewGroup? = null
    private var transformingViewOriginalIndex: Int = -1
    private var transformingView: View? = null
    private var appInstallReceiver: AppInstallReceiver? = null
    private var homeItems = mutableListOf<HomeItem>()
    private var allApps = mutableListOf<AppItem>()
    private var lastGridCol = 0f
    private var lastGridRow = 0f
    private var isStateRestored = false

    private val debounceHandler = Handler(Looper.getMainLooper())
    private val saveStateRunnable = Runnable { saveHomeStateInternal() }
    private val savePageRunnables = mutableMapOf<Int, Runnable>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        applyThemeMode(settingsManager.themeMode)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        model = (application as LauncherApplication).model

        mainLayout = MainLayout(this, this)
        homeView = HomeView(this)
        drawerView = DrawerView(this)
        drawerView?.setColumns(settingsManager.columns)
        drawerView?.setOnAppLongClickListener(object : DrawerView.OnAppLongClickListener {
            override fun onAppLongClick(app: AppItem) {
                mainLayout?.closeDrawer()
                val currentPage = homeView?.getCurrentPage() ?: 0
                val item = HomeItem.createApp(app.packageName, app.className, 0f, 0f, currentPage)
                homeItems.add(item)
                val view = createAppView(item, false)
                homeView?.addItemView(item, view)
                savePage(currentPage)
                if (view != null) mainLayout?.startExternalDrag(view)
            }
        })

        mainLayout?.addView(homeView)
        mainLayout?.addView(drawerView)
        drawerView?.visibility = View.GONE

        mainLayout?.setOnApplyWindowInsetsListener { _, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                drawerView?.setSystemInsets(bars.left, bars.top, bars.right, bars.bottom)
                homeView?.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                drawerView?.setSystemInsets(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
                homeView?.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }

        setContentView(mainLayout)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetHost?.startListening()

        applyDynamicColors()
        loadApps()
        registerAppInstallReceiver()

        homeView?.post {
            restoreHomeState()
            homeView?.setHomeItems(homeItems)
            showDefaultLauncherPrompt()
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return packageName == resolveInfo?.activityInfo?.packageName
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

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)

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

        settingsManager.lastDefaultPromptTimestamp = System.currentTimeMillis()
        settingsManager.incrementDefaultPromptCount()
    }

    override fun saveHomeState() {
        debounceHandler.removeCallbacks(saveStateRunnable)
        debounceHandler.postDelayed(saveStateRunnable, 500)
    }

    private fun saveHomeStateInternal() {
        if (isStateRestored) {
            val pageCount = homeView?.getPageCount() ?: 1
            settingsManager.saveHomeItems(homeItems, pageCount)
        }
    }

    fun savePage(index: Int) {
        var r = savePageRunnables[index]
        if (r == null) {
            r = Runnable {
                if (isStateRestored && index >= 0) {
                    settingsManager.savePageItems(index, homeItems)
                }
            }
            savePageRunnables[index] = r
        }
        debounceHandler.removeCallbacks(r)
        debounceHandler.postDelayed(r, 500)
    }

    override fun isTransforming(): Boolean = currentTransformOverlay != null

    private fun restoreHomeState() {
        homeItems = settingsManager.getHomeItems()
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

    private fun setOverlayBlur(enabled: Boolean, isTransform: Boolean = false) {
        if (!settingsManager.isLiquidGlass) return
        if (!isTransform) {
            homeView?.let { ThemeUtils.applyBlurIfSupported(it, enabled) }
        } else {
            homeView?.let { ThemeUtils.applyBlurIfSupported(it, false) }
        }
        ThemeUtils.applyWindowBlur(window, enabled)
    }

    private fun renderHomeItem(item: HomeItem?) {
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

    private fun createAppView(item: HomeItem, isOnGlass: Boolean): View? {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val scale = settingsManager.iconScale
        val size = (baseSize * scale).toInt()

        val iconView = ImageView(this).apply {
            tag = "item_icon"
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        val labelView = TextView(this).apply {
            tag = "item_label"
            setTextColor(ThemeUtils.getAdaptiveColor(this@MainActivity, settingsManager, isOnGlass))
            textSize = 10 * scale
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val app = findApp(item.packageName)
        if (app != null) {
            model?.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
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
        if (settingsManager.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    private fun createFolderView(item: HomeItem, isOnGlass: Boolean): View? {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val cellWidth = if ((homeView?.width ?: 0) > 0) homeView!!.width / HomeView.GRID_COLUMNS else 1
        val cellHeight = if ((homeView?.height ?: 0) > 0) homeView!!.height / HomeView.GRID_ROWS else 1

        val previewContainer = FrameLayout(this)
        val scale = settingsManager.iconScale
        val sizeW: Int
        val sizeH: Int

        if (item.spanX <= 1.0f && item.spanY <= 1.0f) {
            val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            sizeW = (baseSize * scale).toInt()
            sizeH = sizeW
        } else {
            sizeW = (cellWidth * item.spanX).toInt()
            sizeH = (cellHeight * item.spanY).toInt()
        }

        previewContainer.layoutParams = LinearLayout.LayoutParams(sizeW, sizeH)
        previewContainer.background = ThemeUtils.getGlassDrawable(this, settingsManager, 12f)
        val padding = dpToPx(6)
        previewContainer.setPadding(padding, padding, padding, padding)

        val grid = GridLayout(this).apply {
            tag = "folder_grid"
            columnCount = 2
            rowCount = 2
        }
        previewContainer.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        refreshFolderPreview(item, grid)

        val labelView = TextView(this).apply {
            tag = "item_label"
            setTextColor(ThemeUtils.getAdaptiveColor(this@MainActivity, settingsManager, isOnGlass))
            textSize = 10 * scale
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = if (item.folderName.isNullOrEmpty()) "" else item.folderName
        }

        container.addView(previewContainer)
        container.addView(labelView)
        if (settingsManager.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    fun refreshFolderPreview(folder: HomeItem, grid: GridLayout) {
        grid.removeAllViews()
        val items = folder.folderItems ?: return
        if (items.isEmpty()) return

        val cellWidth = if ((homeView?.width ?: 0) > 0) homeView!!.width / HomeView.GRID_COLUMNS else 1
        val cellHeight = if ((homeView?.height ?: 0) > 0) homeView!!.height / HomeView.GRID_ROWS else 1

        val scale = settingsManager.iconScale
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
                model?.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
                    override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                        iv.setImageBitmap(icon)
                    }
                })
            }
            grid.addView(iv)
        }
    }

    private fun openFolder(folderItem: HomeItem, folderView: View) {
        if (currentFolderOverlay != null) closeFolder()
        setOverlayBlur(true)

        val container = FrameLayout(this).apply {
            setBackgroundColor(0x33000000)
            setOnClickListener { closeFolder() }
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            var startY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startY = event.y
                    MotionEvent.ACTION_UP -> if (event.y - startY > dpToPx(100)) {
                        closeFolder()
                        return true
                    }
                }
                return false
            }
        })

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeUtils.getGlassDrawable(this@MainActivity, settingsManager, 12f)
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            elevation = dpToPx(16).toFloat()
            gravity = Gravity.CENTER_HORIZONTAL
            setOnClickListener { }
        }

        val titleText = TextView(this).apply {
            text = if (folderItem.folderName.isNullOrEmpty()) "Folder" else folderItem.folderName
            setTextColor(ThemeUtils.getAdaptiveColor(this@MainActivity, settingsManager, true))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16))
        }
        overlay.addView(titleText)

        val titleEdit = EditText(this).apply {
            setText(folderItem.folderName)
            setTextColor(ThemeUtils.getAdaptiveColor(this@MainActivity, settingsManager, true))
            background = null
            gravity = Gravity.CENTER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    folderItem.folderName = s.toString()
                    saveHomeState()
                }
                override fun afterTextChanged(s: Editable) {}
            })
        }
        overlay.addView(titleEdit)

        titleText.setOnClickListener {
            titleText.visibility = View.GONE
            titleEdit.visibility = View.VISIBLE
            titleEdit.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        titleEdit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val newName = titleEdit.text.toString()
                folderItem.folderName = newName
                titleText.text = if (newName.isEmpty()) "Folder" else newName
                titleEdit.visibility = View.GONE
                titleText.visibility = View.VISIBLE
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                saveHomeState()
                homeView?.refreshIcons(model!!, allApps)
                true
            } else false
        }

        val grid = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = true
        }

        val folderPadding = dpToPx(8)
        folderItem.folderItems?.let { items ->
            for (sub in items) {
                val subView = createAppView(sub, true) ?: continue
                subView.layoutParams = GridLayout.LayoutParams().apply {
                    setMargins(folderPadding, folderPadding, folderPadding, folderPadding)
                }
                subView.tag = sub
                subView.setOnClickListener {
                    handleAppLaunch(sub.packageName)
                    closeFolder()
                }
                subView.setOnLongClickListener {
                    closeFolder()
                    removeFromFolder(folderItem, sub)
                    homeItems.add(sub)
                    sub.page = homeView?.getCurrentPage() ?: 0
                    homeView?.addItemView(sub, subView)
                    mainLayout?.startExternalDrag(subView)
                    true
                }
                grid.addView(subView)
            }
        }
        overlay.addView(grid)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.setMargins(dpToPx(24), 0, dpToPx(24), 0)
        container.addView(overlay, lp)

        mainLayout?.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        currentFolderOverlay = container
    }

    private fun handleAppLaunch(packageName: String?) {
        packageName ?: return
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) startActivity(intent)
        } catch (ignored: Exception) {
        }
    }

    fun mergeToFolder(target: HomeItem, dragged: HomeItem) {
        val oldDraggedPage = dragged.page
        val targetPage = target.page

        homeItems.remove(dragged)
        homeItems.remove(target)

        val folder = HomeItem.createFolder("", target.col, target.row, targetPage)
        folder.folderItems?.add(target)
        folder.folderItems?.add(dragged)
        folder.rotation = 0f
        folder.scaleX = 1.0f
        folder.scaleY = 1.0f
        folder.tiltX = 0f
        folder.tiltY = 0f
        homeItems.add(folder)

        homeView?.removeItemsByPackage(target.packageName)
        homeView?.removeItemsByPackage(dragged.packageName)
        renderHomeItem(folder)

        if (oldDraggedPage != targetPage) savePage(oldDraggedPage)
        savePage(targetPage)
    }

    fun addToFolder(folder: HomeItem, dragged: HomeItem) {
        val oldDraggedPage = dragged.page
        val targetPage = folder.page

        homeItems.remove(dragged)
        folder.folderItems?.add(dragged)

        homeView?.removeItemsByPackage(dragged.packageName)
        refreshFolderIconsOnHome(folder)

        if (oldDraggedPage != targetPage) savePage(oldDraggedPage)
        savePage(targetPage)
    }

    private fun removeFromFolder(folder: HomeItem, item: HomeItem) {
        val page = folder.page
        folder.folderItems?.remove(item)
        if (folder.folderItems?.size == 1) {
            val lastItem = folder.folderItems!![0]
            homeItems.remove(folder)
            lastItem.col = folder.col
            lastItem.row = folder.row
            lastItem.page = page
            lastItem.rotation = folder.rotation
            lastItem.scaleX = folder.scaleX
            lastItem.scaleY = folder.scaleY
            lastItem.tiltX = folder.tiltX
            lastItem.tiltY = folder.tiltY
            homeItems.add(lastItem)

            removeFolderView(folder)
            renderHomeItem(lastItem)
        } else {
            refreshFolderIconsOnHome(folder)
        }
        savePage(page)
    }

    private fun removeFolderView(folder: HomeItem) {
        val pagesContainer = homeView?.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until pagesContainer.childCount) {
            val page = pagesContainer.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until page.childCount) {
                val v = page.getChildAt(j)
                if (v.tag === folder) {
                    page.removeView(v)
                    return
                }
            }
        }
    }

    private fun refreshFolderIconsOnHome(folder: HomeItem) {
        val pagesContainer = homeView?.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until pagesContainer.childCount) {
            val page = pagesContainer.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until page.childCount) {
                val v = page.getChildAt(j)
                if (v.tag === folder) {
                    val grid = findGridLayout(v as ViewGroup)
                    if (grid != null) refreshFolderPreview(folder, grid)
                    return
                }
            }
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

    private fun createWidgetView(item: HomeItem): View? {
        val am = appWidgetManager ?: return null
        val ah = appWidgetHost ?: return null
        val info = am.getAppWidgetInfo(item.widgetId) ?: return null
        return try {
            val hostView = ah.createView(this, item.widgetId, info)
            hostView?.setAppWidget(item.widgetId, info)
            hostView
        } catch (e: Exception) {
            null
        }
    }

    private fun showWidgetOptions(item: HomeItem, hostView: View) {
        val options = arrayOf(getString(R.string.action_resize), getString(R.string.action_remove))
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(options) { _, which ->
                if (which == 0) showResizeDialog(item, hostView)
                else removeHomeItem(item, hostView)
            }.create()
        dialog.show()
        dialog.window?.let {
            it.setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager))
            ThemeUtils.applyWindowBlur(it, settingsManager.isLiquidGlass)
        }
    }

    private fun showResizeDialog(item: HomeItem, hostView: View) {
        val sizes = arrayOf("1x1", "2x1", "2x2", "4x2", "4x1")
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_resize_widget)
            .setItems(sizes) { _, which ->
                when (which) {
                    0 -> { item.spanX = 1f; item.spanY = 1f }
                    1 -> { item.spanX = 2f; item.spanY = 1f }
                    2 -> { item.spanX = 2f; item.spanY = 2f }
                    3 -> { item.spanX = 4f; item.spanY = 2f }
                    4 -> { item.spanX = 4f; item.spanY = 1f }
                }
                homeView?.updateViewPosition(item, hostView)
                saveHomeState()
            }.create()
        dialog.show()
        dialog.window?.let {
            it.setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager))
            ThemeUtils.applyWindowBlur(it, settingsManager.isLiquidGlass)
        }
    }

    override fun removeHomeItem(item: HomeItem, v: View) {
        val page = item.page
        homeItems.remove(item)
        (v.parent as? ViewGroup)?.removeView(v)
        savePage(page)
        homeView?.cleanupEmptyPages()
        homeView?.refreshIcons(model!!, allApps)
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
            homeView?.refreshIcons(model!!, allApps)
            saveHomeState()
        }
    }

    override fun showAppInfo(item: HomeItem) {
        if (item.packageName.isNullOrEmpty()) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${item.packageName}")
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
            setTextColor(ThemeUtils.getAdaptiveColor(this@MainActivity, settingsManager, false))
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }

        val tvDate = TextView(this).apply {
            textSize = 18f
            val adaptiveDim = ThemeUtils.getAdaptiveColor(this@MainActivity, settingsManager, false) and 0xBBFFFFFF.toInt()
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
        val optionsList = mutableListOf<String>()
        val iconsList = mutableListOf<Int>()

        optionsList.add(getString(R.string.menu_widgets))
        iconsList.add(R.drawable.ic_widgets)
        optionsList.add(getString(R.string.menu_wallpaper))
        iconsList.add(R.drawable.ic_wallpaper)
        optionsList.add(getString(R.string.menu_settings))
        iconsList.add(R.drawable.ic_settings)
        optionsList.add(getString(R.string.layout_add_page))
        iconsList.add(R.drawable.ic_layout)

        if (homeView?.getPageCount() ?: 0 > 1) {
            optionsList.add(getString(R.string.layout_remove_page))
            iconsList.add(R.drawable.ic_remove)
        }

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.select_dialog_item, android.R.id.text1, optionsList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<TextView>(android.R.id.text1)
                tv.setCompoundDrawablesWithIntrinsicBounds(iconsList[position], 0, 0, 0)
                tv.compoundDrawablePadding = dpToPx(16)
                tv.setTextColor(adaptiveColor)
                tv.compoundDrawables[0]?.setTint(adaptiveColor)
                return view
            }
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_home_menu)
            .setAdapter(adapter) { _, which ->
                when (optionsList[which]) {
                    getString(R.string.menu_widgets) -> {
                        if (!settingsManager.isFreeformHome) {
                            lastGridCol = Math.round(col).toFloat()
                            lastGridRow = Math.round(row).toFloat()
                        } else {
                            lastGridCol = col
                            lastGridRow = row
                        }
                        pickWidget()
                    }
                    getString(R.string.menu_wallpaper) -> openWallpaperPicker()
                    getString(R.string.menu_settings) -> openSettings()
                    getString(R.string.layout_add_page) -> {
                        homeView?.addPage()
                        saveHomeState()
                        Toast.makeText(this, R.string.page_added, Toast.LENGTH_SHORT).show()
                    }
                    getString(R.string.layout_remove_page) -> {
                        homeView?.let {
                            if (it.getPageCount() > 1) {
                                it.removePage(it.getCurrentPage())
                                saveHomeState()
                            }
                        }
                    }
                }
            }.create()
        dialog.show()
        dialog.window?.let {
            it.setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager))
            ThemeUtils.applyWindowBlur(it, settingsManager.isLiquidGlass)
        }
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
            startActivityForResult(Intent(this, SettingsActivity::class.java), 100)
        } catch (e: Exception) {
            Toast.makeText(this, "Launcher settings could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyDynamicColors() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val accentColor = resources.getColor(android.R.color.system_accent1_400, theme)
                homeView?.setAccentColor(accentColor)
                drawerView?.setAccentColor(accentColor)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun loadApps() {
        model?.loadApps(object : LauncherModel.OnAppsLoadedListener {
            override fun onAppsLoaded(apps: List<AppItem>) {
                allApps = apps.toMutableList()
                drawerView?.setApps(allApps, model)
                homeView?.refreshIcons(model!!, allApps)
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
        model?.onTrimMemory(level)
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

    override fun onBackPressed() {
        if (currentTransformOverlay != null) {
            closeTransformOverlay()
            return
        }
        if (currentFolderOverlay != null) {
            closeFolder()
            return
        }
        if (mainLayout?.isDrawerOpen == true) {
            mainLayout?.closeDrawer()
        }
    }

    private fun updateHomeItemFromTransform() {
        val v = transformingView ?: return
        val parent = transformingViewOriginalParent ?: return
        val item = v.tag as HomeItem
        val isFreeform = settingsManager.isFreeformHome

        homeView?.let {
            item.page = it.getCurrentPage()
            val pagesContainer = it.getChildAt(0) as? ViewGroup
            if (pagesContainer != null && item.page < pagesContainer.childCount) {
                transformingViewOriginalParent = pagesContainer.getChildAt(item.page) as? ViewGroup
                transformingViewOriginalIndex = -1
            }
        }

        val cellWidth = transformingViewOriginalParent!!.width / HomeView.GRID_COLUMNS
        val cellHeight = transformingViewOriginalParent!!.height / HomeView.GRID_ROWS

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
        transformingViewOriginalParent!!.getLocationOnScreen(pagePos)
        val rootPos = IntArray(2)
        mainLayout?.getLocationOnScreen(rootPos)

        val xInParent = v.x - (pagePos[0] - rootPos[0])
        val yInParent = v.y - (pagePos[1] - rootPos[1])

        if (cellWidth > 0) item.col = if (isFreeform) xInParent / cellWidth else Math.round(xInParent / cellWidth).toFloat()
        if (cellHeight > 0) item.row = if (isFreeform) yInParent / cellHeight else Math.round(yInParent / cellHeight).toFloat()

        val centerX = v.x + v.width / 2f
        val centerY = v.y + v.height / 2f
        val collisionView = findHomeItemAtRoot(centerX, centerY, v)
        if (collisionView != null && item.type == HomeItem.Type.APP) {
            val target = collisionView.tag as? HomeItem
            if (target != null) {
                if (target.type == HomeItem.Type.APP) {
                    mainLayout?.removeView(v)
                    mergeToFolder(target, item)
                    transformingView = null
                    return
                } else if (target.type == HomeItem.Type.FOLDER) {
                    mainLayout?.removeView(v)
                    addToFolder(target, item)
                    transformingView = null
                    return
                }
            }
        }
    }

    override fun showTransformOverlay(targetView: View) {
        if (currentTransformOverlay != null) return
        setOverlayBlur(true, true)
        transformingView = targetView
        transformingViewOriginalParent = targetView.parent as? ViewGroup
        transformingViewOriginalIndex = transformingViewOriginalParent?.indexOfChild(targetView) ?: -1

        var x = targetView.x
        var y = targetView.y
        var p = targetView.parent
        while (p != null && p !== mainLayout) {
            if (p is View) {
                x += p.x
                y += p.y
            }
            p = p.parent
        }

        transformingViewOriginalParent?.removeView(targetView)
        mainLayout?.addView(targetView)
        targetView.x = x
        targetView.y = y

        currentTransformOverlay = TransformOverlay(this, targetView, settingsManager, object : TransformOverlay.OnSaveListener {
            override fun onMove(x: Float, y: Float) {
                homeView?.checkEdgeScrollLoopStart(x)
            }
            override fun onMoveStart(x: Float, y: Float) {
                val item = targetView.tag as? HomeItem
                homeView?.setInitialDragState(x, item?.page ?: -1)
            }
            override fun onSave() {
                updateHomeItemFromTransform()
                saveHomeState()
                closeTransformOverlay()
            }
            override fun onCancel() {
                closeTransformOverlay()
            }
            override fun onRemove() {
                removeHomeItem(targetView.tag as HomeItem, targetView)
                transformingView = null
                closeTransformOverlay()
            }
            override fun onAppInfo() {
                showAppInfo(targetView.tag as HomeItem)
            }
            override fun onCollision(otherView: View) {
                updateHomeItemFromTransform()
                saveHomeState()
                closeTransformOverlay()
                showTransformOverlay(otherView)
            }
            override fun findItemAt(x: Float, y: Float, exclude: View): View? {
                return findHomeItemAtRoot(x, y, exclude)
            }
        })
        mainLayout?.addView(currentTransformOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun startTransformDirectMove(v: View, x: Float, y: Float) {
        currentTransformOverlay?.startDirectMove(x, y)
    }

    private fun findHomeItemAtRoot(x: Float, y: Float, exclude: View): View? {
        val hv = homeView ?: return null
        val page = hv.getCurrentPage()
        val pagesContainer = hv.getChildAt(0) as? ViewGroup ?: return null
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
        currentTransformOverlay?.let {
            mainLayout?.removeView(it)
            currentTransformOverlay = null

            if (transformingView != null && transformingViewOriginalParent != null) {
                mainLayout?.removeView(transformingView)
                transformingViewOriginalParent?.addView(transformingView, transformingViewOriginalIndex)
                homeView?.updateViewPosition(transformingView?.tag as HomeItem, transformingView!!)
            }
            setOverlayBlur(false, true)
            transformingView = null
            transformingViewOriginalParent = null
            homeView?.cleanupEmptyPages()
            homeView?.refreshIcons(model!!, allApps)
        }
    }

    private fun closeFolder() {
        currentFolderOverlay?.let {
            mainLayout?.removeView(it)
            currentFolderOverlay = null
            setOverlayBlur(false)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(mainLayout?.windowToken, 0)
            homeView?.refreshIcons(model!!, allApps)
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeUtils.updateStatusBarContrast(this)
        mainLayout?.updateDimVisibility()
        homeView?.refreshLayout()
        homeView?.refreshIcons(model!!, allApps)
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
        model = null
        mainLayout = null
        homeView = null
        drawerView = null
        appInstallReceiver = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            loadApps()
            homeView?.refreshLayout()
            homeView?.refreshIcons(model!!, allApps)
            return
        }
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_PICK_APPWIDGET) configureWidget(data)
            else if (requestCode == REQUEST_CREATE_APPWIDGET) createWidget(data)
        }
    }

    private fun configureWidget(data: Intent) {
        val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val info = appWidgetManager?.getAppWidgetInfo(appWidgetId) ?: return
        if (info.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET)
        } else createWidget(data)
    }

    private fun createWidget(data: Intent) {
        val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val item = HomeItem.createWidget(appWidgetId, lastGridCol, lastGridRow, 2f, 1f, homeView?.getCurrentPage() ?: 0)
        homeItems.add(item)
        renderHomeItem(item)
        saveHomeState()
    }

    fun pickWidget() {
        val am = appWidgetManager ?: return
        val providers = am.installedProviders ?: return
        val grouped = mutableMapOf<String, MutableList<AppWidgetProviderInfo>>()
        for (info in providers) {
            val pkg = info.provider.packageName
            grouped.getOrPut(pkg) { mutableListOf() }.add(info)
        }

        val packages = grouped.keys.sortedWith { a, b -> getAppName(a).compareTo(getAppName(b), true) }

        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        dialog.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            ThemeUtils.applyWindowBlur(it, settingsManager.isLiquidGlass)
        }

        val root = FrameLayout(this).apply { background = ThemeUtils.getGlassDrawable(this@MainActivity, settingsManager, 0f) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(container)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)
        val secondaryColor = (adaptiveColor and 0x00FFFFFF) or 0x80000000.toInt()

        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(32))
        }

        val titleIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_widgets)
            setColorFilter(adaptiveColor)
        }
        titleLayout.addView(titleIcon, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply { rightMargin = dpToPx(16) })

        val title = TextView(this).apply {
            setText(R.string.title_pick_widget)
            textSize = 32f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(adaptiveColor)
        }
        titleLayout.addView(title)
        container.addView(titleLayout)

        val scrollView = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val itemsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(itemsContainer)
        container.addView(scrollView)

        for (pkg in packages) {
            val header = TextView(this).apply {
                text = getAppName(pkg)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(secondaryColor)
                isAllCaps = true
                setPadding(0, dpToPx(24), 0, dpToPx(12))
            }
            itemsContainer.addView(header)

            grouped[pkg]?.let { infos ->
                for (info in infos) {
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                        isClickable = true
                        isFocusable = true
                    }

                    val cardBg = GradientDrawable()
                    val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val cardColor = if (settingsManager.isLiquidGlass) 0x1AFFFFFF else (if (isNight) 0x1AFFFFFF else 0x0D000000)
                    cardBg.setColor(cardColor)
                    cardBg.cornerRadius = dpToPx(16).toFloat()
                    card.background = cardBg

                    itemsContainer.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpToPx(12) })

                    val preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    var sX = info.minWidth.toFloat() / (resources.displayMetrics.widthPixels / HomeView.GRID_COLUMNS)
                    var sY = info.minHeight.toFloat() / (resources.displayMetrics.heightPixels / HomeView.GRID_ROWS)
                    if (!settingsManager.isFreeformHome) {
                        sX = Math.max(1f, Math.ceil(sX.toDouble()).toFloat())
                        sY = Math.max(1f, Math.ceil(sY.toDouble()).toFloat())
                    }
                    val spanX = sX
                    val spanY = sY

                    widgetPreviewExecutor.execute {
                        try {
                            val previewDrawable = info.loadPreviewImage(this@MainActivity, 0) ?: info.loadIcon(this@MainActivity, 0)
                            runOnUiThread { preview.setImageDrawable(previewDrawable) }
                        } catch (ignored: Exception) { }
                    }
                    card.addView(preview, LinearLayout.LayoutParams(dpToPx(64), dpToPx(64)).apply { rightMargin = dpToPx(16) })

                    val textLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    textLayout.addView(TextView(this).apply {
                        text = info.label
                        setTextColor(adaptiveColor)
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                    })
                    textLayout.addView(TextView(this).apply {
                        text = getString(R.string.widget_size_format, Math.ceil(spanX.toDouble()).toInt(), Math.ceil(spanY.toDouble()).toInt())
                        textSize = 12f
                        setTextColor(secondaryColor)
                    })
                    card.addView(textLayout)

                    card.setOnClickListener {
                        dialog.dismiss()
                        val appWidgetId = appWidgetHost!!.allocateAppWidgetId()
                        if (am.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)) {
                            val homeItem = HomeItem.createWidget(appWidgetId, lastGridCol, lastGridRow, spanX, spanY, homeView?.getCurrentPage() ?: 0)
                            homeItems.add(homeItem)
                            renderHomeItem(homeItem)
                            saveHomeState()
                        } else {
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                            }
                            startActivityForResult(intent, REQUEST_PICK_APPWIDGET)
                        }
                    }
                }
            }
        }

        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(adaptiveColor)
            alpha = 0.6f
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(closeBtn, FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.TOP or Gravity.RIGHT).apply { topMargin = dpToPx(16); rightMargin = dpToPx(16) })

        root.setOnApplyWindowInsetsListener { _, insets ->
            val top = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) insets.getInsets(android.view.WindowInsets.Type.systemBars()).top else insets.systemWindowInsetTop
            val bottom = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom else insets.systemWindowInsetBottom
            container.setPadding(dpToPx(24), top + dpToPx(64), dpToPx(24), bottom + dpToPx(24))
            val clp = closeBtn.layoutParams as FrameLayout.LayoutParams
            clp.topMargin = top + dpToPx(16)
            closeBtn.layoutParams = clp
            insets
        }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) { packageName }
    }

    private fun applyThemeMode(mode: String?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val nightMode = when (mode) {
                "light" -> UiModeManager.MODE_NIGHT_NO
                "dark" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            if (uiModeManager.nightMode != nightMode) uiModeManager.setApplicationNightMode(nightMode)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    override fun isFolderOpen(): Boolean = currentFolderOverlay != null
    override fun getHomeView(): HomeView? = homeView
    override fun getDrawerView(): DrawerView? = drawerView
    override fun getSettingsManager(): SettingsManager = settingsManager
    override fun getCurrentPage(): Int = homeView?.getCurrentPage() ?: 0
    override fun scrollToPage(page: Int) { homeView?.scrollToPage(page) }

    override fun getLauncherModel(): LauncherModel? = model

    override fun handleItemClick(v: View) {
        val item = v.tag as? HomeItem ?: return
        when (item.type) {
            HomeItem.Type.APP -> {
                handleAppLaunch(item.packageName)
            }
            HomeItem.Type.FOLDER -> openFolder(item, v)
            HomeItem.Type.WIDGET -> showWidgetOptions(item, v)
            else -> {}
        }
    }

    override fun openDrawer() {
        mainLayout?.openDrawer()
    }

    override fun closeDrawer() {
        mainLayout?.closeDrawer()
    }
}
