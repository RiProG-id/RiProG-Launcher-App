package com.riprog.launcher.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.annotation.SuppressLint
import android.widget.TextView
import com.riprog.launcher.R
import com.riprog.launcher.ui.home.manager.GridManager
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.repository.AppLoader
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import com.riprog.launcher.ui.common.ThemeUtils

@SuppressLint("ViewConstructor")
class HomeView(context: Context, private val settingsManager: LauncherPreferences) : FrameLayout(context) {
    val pagesContainer: LinearLayout = LinearLayout(context)
    private val pageIndicator: PageIndicator = PageIndicator(context)
    val pageManager: PageManager
    val gridManager: GridManager = GridManager()
    private var homeItems: List<HomeItem>? = null
    private var accentColor = Color.WHITE
    private var appLoader: AppLoader? = null
    private var allApps: List<AppItem>? = null

    private val tempMatrix = Matrix()

    private var draggingView: View? = null
    private var isExternalDrag = false
    private var lastX = 0f
    private var lastY = 0f
    private var initialPage = -1

    private var lastPageSwitchTime: Long = 0
    private var initialDragX = 0f
    private var edgeStartTime: Long = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val edgeScrollHandler = Handler(Looper.getMainLooper())
    private var isEdgeScrolling = false

    private val edgeScrollRunnable = object : Runnable {
        override fun run() {
            if (draggingView != null || (context is MainActivity && context.isTransforming())) {
                checkEdgeScrollLoop(lastX)
                edgeScrollHandler.postDelayed(this, 400)
            } else {
                isEdgeScrolling = false
            }
        }
    }

    init {
        pagesContainer.orientation = LinearLayout.HORIZONTAL
        pagesContainer.clipChildren = false
        pagesContainer.clipToPadding = false
        addView(pagesContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val indicatorParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        indicatorParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        indicatorParams.bottomMargin = dpToPx(48)
        addView(pageIndicator, indicatorParams)

        pageManager = PageManager(pagesContainer, pageIndicator, settingsManager) {
            refreshIconsDebounced()
        }

        addDrawerHint()
        post { pageManager.cleanupEmptyPages() }
    }

    fun checkEdgeScrollLoop(x: Float) {
        lastX = x
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPageSwitchTime < PAGE_SWITCH_COOLDOWN) return

        if (Math.abs(x - initialDragX) < dpToPx(MIN_DRAG_DISTANCE_DP)) return

        if (x < width * EDGE_THRESHOLD) {
            scrollToPage(getCurrentPage() - 1)
            lastPageSwitchTime = currentTime
        } else if (x > width * (1f - EDGE_THRESHOLD)) {
            scrollToPage(getCurrentPage() + 1)
            lastPageSwitchTime = currentTime
        }
    }

    fun addPageAtIndex(index: Int) = pageManager.addPageAtIndex(index)

    private fun addDrawerHint() {
        if (settingsManager.drawerOpenCount >= 5) return

        val hint = TextView(context)
        hint.setText(R.string.drawer_hint)
        hint.textSize = 12f
        hint.setTextColor(context.getColor(R.color.foreground_dim))
        hint.alpha = 0f
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.bottomMargin = dpToPx(120)
        addView(hint, lp)

        if (Math.random() < 0.3) {
            hint.animate().alpha(1f).setDuration(1000).setStartDelay(2000).withEndAction {
                hint.animate().alpha(0f).setDuration(1000).setStartDelay(4000).withEndAction {
                    removeView(hint)
                }.start()
            }.start()
        }
    }

    fun addPage() = pageManager.addPage()

    fun addItemView(item: HomeItem?, view: View?) {
        if (item == null || view == null) return

        val existing = findViewForItem(item)
        if (existing != null && existing !== view) {
            val parent = existing.parent as? ViewGroup
            parent?.removeView(existing)
        }

        while (item.page >= pageManager.getPageCount()) {
            pageManager.addPage()
        }
        val parent = view.parent as? ViewGroup
        parent?.removeView(view)

        val page = pageManager.getPageAt(item.page)
        if (page != null) {
            view.tag = item
            view.visibility = INVISIBLE
            page.addView(view)
            updateViewPosition(item, view)
        }
    }

    fun updateViewPosition(item: HomeItem, view: View) {
        val availW = width - paddingLeft - paddingRight
        val availH = height - paddingTop - paddingBottom

        val cellWidth = gridManager.getCellWidth(availW)
        val cellHeight = gridManager.getCellHeight(availH)

        if (cellWidth <= 0 || cellHeight <= 0) {
            view.visibility = INVISIBLE
            post {
                updateViewPosition(item, view)
                view.visibility = VISIBLE
            }
            return
        }
        view.visibility = VISIBLE

        val lp: LayoutParams
        if (item.type == HomeItem.Type.WIDGET || (item.type == HomeItem.Type.FOLDER && (item.spanX > 1.0f || item.spanY > 1.0f))) {
            lp = LayoutParams((cellWidth * item.spanX).toInt(), (cellHeight * item.spanY).toInt())
        } else {
            val size = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            lp = LayoutParams(size * 2, size * 2)
        }
        view.layoutParams = lp

        if (item.type == HomeItem.Type.APP || (item.type == HomeItem.Type.FOLDER && item.spanX <= 1.0f && item.spanY <= 1.0f)) {
            val offsetX = if (settingsManager.isFreeformHome) 0f else (cellWidth - lp.width) / 2f
            val offsetY = if (settingsManager.isFreeformHome) 0f else (cellHeight - lp.height) / 2f
            view.x = item.col * cellWidth + offsetX
            view.y = item.row * cellHeight + offsetY
        } else {
            view.x = item.col * cellWidth
            view.y = item.row * cellHeight
        }

        view.rotation = item.rotation
        view.scaleX = item.scaleX
        view.scaleY = item.scaleY
        view.rotationX = item.tiltX
        view.rotationY = item.tiltY
    }

    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    fun startDragging(v: View, x: Float, y: Float, isExternal: Boolean = false) {
        draggingView = v
        this.isExternalDrag = isExternal
        lastX = x
        lastY = y
        initialDragX = x
        val item = v.tag as? HomeItem
        if (item != null) initialPage = item.page

        if (isExternal && v.layoutParams != null && v.layoutParams.width > 0) {
            dragOffsetX = v.layoutParams.width / 2f
            dragOffsetY = v.layoutParams.height / 2f
        } else {
            val vPos = IntArray(2)
            v.getLocationOnScreen(vPos)
            dragOffsetX = x - vPos[0]
            dragOffsetY = y - vPos[1]
        }

        if (context is MainActivity) {
            val root = (context as MainActivity).findViewById<ViewGroup>(android.R.id.content) ?: return

            var absX = v.x
            var absY = v.y
            var p = v.parent
            while (p != null && p is View && p !== root) {
                absX += p.x
                absY += p.y
                p = p.parent
            }

            (v.parent as? ViewGroup)?.removeView(v)
            root.addView(v)
            v.x = absX
            v.y = absY
        }

        v.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.8f).setDuration(0).start()
        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    fun handleDrag(x: Float, y: Float) {
        draggingView?.let {
            it.x = x - dragOffsetX
            it.y = y - dragOffsetY
            lastX = x
            lastY = y
            checkEdgeScrollLoopStart(x)
        }
    }

    fun setInitialDragState(x: Float, page: Int) {
        this.initialDragX = x
        this.initialPage = page
    }

    fun checkEdgeScrollLoopStart(x: Float) {
        lastX = x
        if (x < width * EDGE_THRESHOLD || x > width * (1f - EDGE_THRESHOLD)) {
            if (!isEdgeScrolling) {
                isEdgeScrolling = true
                edgeStartTime = System.currentTimeMillis()
                edgeScrollHandler.postDelayed(edgeScrollRunnable, HOLD_DELAY)
            }
        } else {
            isEdgeScrolling = false
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
        }
    }

    fun endDragging() {
        draggingView?.let { v ->
            v.scaleX = 1.0f
            v.scaleY = 1.0f
            v.alpha = 1.0f
            val item = v.tag as? HomeItem
            if (item != null) {
                snapToGrid(item, v)
            }
            draggingView = null
            isEdgeScrolling = false
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
            pageManager.cleanupEmptyPages()
            if (appLoader != null && allApps != null) {
                refreshIcons(appLoader!!, allApps!!)
            }
        }
    }

    fun cancelDragging() {
        draggingView?.let { v ->
            v.scaleX = 1.0f
            v.scaleY = 1.0f
            v.alpha = 1.0f
            val item = v.tag as? HomeItem
            if (item != null) {
                addItemView(item, v)
            }
        }
        draggingView = null
        isEdgeScrolling = false
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
    }

    fun clearDraggingView() {
        draggingView = null
        isEdgeScrolling = false
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
    }

    fun removePage(index: Int) {
        val oldPageCount = pageManager.getPageCount()
        if (index < 0 || index >= oldPageCount || oldPageCount <= 1) return

        homeItems?.let { items ->
            val mutableItems = items.toMutableList()
            val iterator = mutableItems.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.page == index) {
                    iterator.remove()
                } else if (item.page > index) {
                    item.page--
                }
            }
            homeItems = mutableItems
        }

        pageManager.removePage(index) {
        }
        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
    }

    fun removeItemsByPackage(packageName: String?) {
        if (packageName == null) return
        var changed = false
        for (page in pageManager.getAllPages()) {
            for (i in page.childCount - 1 downTo 0) {
                val v = page.getChildAt(i)
                val item = v?.tag as? HomeItem
                if (item?.type == HomeItem.Type.APP && packageName == item.packageName) {
                    page.removeView(v)
                    changed = true
                }
            }
        }
        if (changed) {
            pageManager.cleanupEmptyPages()
        }
    }

    fun isAreaOccupied(col: Int, row: Int, spanX: Int, spanY: Int, page: Int, exclude: HomeItem?): Boolean {
        for (item in homeItems ?: return false) {
            if (item === exclude || item.page != page) continue
            val itemCol = Math.round(item.col)
            val itemRow = Math.round(item.row)
            val itemSpanX = Math.max(1, Math.round(item.spanX))
            val itemSpanY = Math.max(1, Math.round(item.spanY))
            if (col < itemCol + itemSpanX && col + spanX > itemCol &&
                row < itemRow + itemSpanY && row + spanY > itemRow) {
                return true
            }
        }
        return false
    }

    fun findNearestEmptySpot(item: HomeItem, page: Int = -1): Pair<Int, Int>? {
        val targetPage = if (page >= 0) page else item.page
        val spanX = Math.round(item.spanX)
        val spanY = Math.round(item.spanY)
        for (r in 0 until gridManager.rows - spanY + 1) {
            for (c in 0 until gridManager.columns - spanX + 1) {
                if (!isAreaOccupied(c, r, spanX, spanY, targetPage, item)) {
                    return Pair(c, r)
                }
            }
        }
        return null
    }

    private fun findFirstEmptySpotGlobally(item: HomeItem): Triple<Int, Int, Int>? {
        for (p in 0 until pageManager.getPageCount()) {
            val spot = findNearestEmptySpot(item, p)
            if (spot != null) return Triple(spot.first, spot.second, p)
        }
        return null
    }

    private fun findCollision(draggedView: View): HomeItem? {
        val currentPageLayout = pageManager.getPageAt(pageManager.getCurrentPage()) ?: return null

        val draggedPos = IntArray(2)
        draggedView.getLocationOnScreen(draggedPos)
        val centerX = draggedPos[0] + draggedView.width / 2f
        val centerY = draggedPos[1] + draggedView.height / 2f

        val pagePos = IntArray(2)
        currentPageLayout.getLocationOnScreen(pagePos)

        for (i in 0 until currentPageLayout.childCount) {
            val child = currentPageLayout.getChildAt(i)
            if (child === draggedView) continue

            val targetItem = child.tag as? HomeItem ?: continue

            val childLeft = pagePos[0] + child.x
            val childTop = pagePos[1] + child.y

            if (centerX >= childLeft && centerX <= childLeft + child.width &&
                centerY >= childTop && centerY <= childTop + child.height
            ) {
                return targetItem
            }
        }
        return null
    }

    private fun getRelativeCoords(v: View): FloatArray {
        val homePos = IntArray(2)
        getLocationOnScreen(homePos)
        val vPos = IntArray(2)
        v.getLocationOnScreen(vPos)
        val xInHome = (vPos[0] - homePos[0]).toFloat()
        val yInHome = (vPos[1] - homePos[1]).toFloat()
        return floatArrayOf(xInHome, yInHome)
    }

    private fun snapToGrid(item: HomeItem, v: View) {
        val availW = width - paddingLeft - paddingRight
        val availH = height - paddingTop - paddingBottom

        val cellWidth = gridManager.getCellWidth(availW).let { if (it > 0) it else 1 }
        val cellHeight = gridManager.getCellHeight(availH).let { if (it > 0) it else 1 }

        val coords = getRelativeCoords(v)
        val xInHome = coords[0] - paddingLeft
        val yInHome = coords[1] - paddingTop

        val oldPage = initialPage
        item.page = pageManager.getCurrentPage()

        val target = findCollision(v)
        if (target != null && item.type == HomeItem.Type.APP) {
            if (target.type == HomeItem.Type.APP || target.type == HomeItem.Type.FOLDER) {
                if (context is MainActivity) {
                    (v.parent as? ViewGroup)?.removeView(v)
                    if (target.type == HomeItem.Type.APP) (context as MainActivity).mergeToFolder(target, item)
                    else (context as MainActivity).addToFolder(target, item)
                    return
                }
            }
        }

        if (settingsManager.isFreeformHome) {
            item.col = xInHome / cellWidth.toFloat()
            item.row = yInHome / cellHeight.toFloat()
            item.rotation = v.rotation
            item.scaleX = v.scaleX
            item.scaleY = v.scaleY
            item.tiltX = v.rotationX
            item.tiltY = v.rotationY
        } else {
            val targetCol = Math.max(0, Math.min(gridManager.columns - item.spanX.toInt(), Math.round(xInHome / cellWidth.toFloat())))
            val targetRow = Math.max(0, Math.min(gridManager.rows - item.spanY.toInt(), Math.round(yInHome / cellHeight.toFloat())))
            if (isAreaOccupied(targetCol, targetRow, Math.round(item.spanX), Math.round(item.spanY), item.page, item)) {
                val spot = findFirstEmptySpotGlobally(item)
                if (spot != null) {
                    item.col = spot.first.toFloat()
                    item.row = spot.second.toFloat()
                    item.page = spot.third
                } else {
                    val newPageIndex = pageManager.addPage()
                    item.page = newPageIndex
                    item.col = 0f
                    item.row = 0f
                }
            } else {
                item.col = targetCol.toFloat()
                item.row = targetRow.toFloat()
            }
            item.rotation = 0f
            item.scaleX = 1.0f
            item.scaleY = 1.0f
            item.tiltX = 0f
            item.tiltY = 0f
        }

        if (isExternalDrag && context is MainActivity) {
            (v.parent as? ViewGroup)?.removeView(v)
            (context as MainActivity).onExternalItemDropped(item)
        } else {
            addItemView(item, v)
        }

        if (context is MainActivity) {
            if (oldPage != -1 && oldPage != item.page) {
                (context as MainActivity).savePage(oldPage)
            }
            (context as MainActivity).savePage(item.page)
        }
    }

    fun scrollToPage(page: Int) = pageManager.scrollToPage(page)

    fun getCurrentPage(): Int = pageManager.getCurrentPage()

    fun getPageCount(): Int = pageManager.getPageCount()

    private val refreshIconsRunnable = Runnable {
        if (appLoader != null && allApps != null) {
            refreshIconsInternal(appLoader!!, allApps!!)
        }
    }

    fun refreshIconsDebounced() {
        mainHandler.removeCallbacks(refreshIconsRunnable)
        mainHandler.postDelayed(refreshIconsRunnable, 100)
    }

    fun setHomeItems(items: List<HomeItem>) {
        this.homeItems = items
    }

    fun clearAllItems() {
        for (page in pageManager.getAllPages()) {
            page.removeAllViews()
        }
    }

    fun refreshIcons(appLoader: AppLoader, allApps: List<AppItem>) {
        this.appLoader = appLoader
        this.allApps = allApps
        refreshIconsDebounced()
    }

    fun getVisualRect(v: View?): RectF {
        if (v == null) return RectF()
        val rect = RectF(0f, 0f, v.width.toFloat(), v.height.toFloat())
        tempMatrix.set(v.matrix)
        tempMatrix.postTranslate(v.left.toFloat(), v.top.toFloat())
        tempMatrix.mapRect(rect)
        return rect
    }

    fun findViewForItem(item: HomeItem): View? {
        for (pageLayout in pageManager.getAllPages()) {
            for (i in 0 until pageLayout.childCount) {
                val v = pageLayout.getChildAt(i)
                if (v.tag === item) return v
            }
        }
        return null
    }

    private fun refreshIconsInternal(appLoader: AppLoader, allApps: List<AppItem>) {
        val globalScale = if (settingsManager.isFreeformHome) 1.0f else settingsManager.iconScale
        val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val targetIconSize = (baseSize * globalScale).toInt()
        val hideLabels = settingsManager.isHideLabels

        val appMap = mutableMapOf<String, AppItem>()
        for (a in allApps) {
            appMap[a.packageName] = a
        }

        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, false)
        val allPages = pageManager.getAllPages()

        for (page in allPages) {
            refreshPageIcons(page, appLoader, appMap, targetIconSize, globalScale, hideLabels, adaptiveColor)
        }
    }

    private fun refreshPageIcons(
        page: FrameLayout, appLoader: AppLoader, appMap: Map<String, AppItem>,
        targetIconSize: Int, globalScale: Float, hideLabels: Boolean, adaptiveColor: Int
    ) {
        for (i in 0 until page.childCount) {
            val view = page.getChildAt(i)
            val item = view.tag as? HomeItem
            if (item == null || view !is ViewGroup) continue

            if (item.type == HomeItem.Type.APP) {
                val iv = view.findViewWithTag<ImageView>("item_icon")
                val tv = view.findViewWithTag<TextView>("item_label")

                if (iv != null) {
                    val lp = iv.layoutParams
                    if (lp.width != targetIconSize) {
                        lp.width = targetIconSize
                        lp.height = targetIconSize
                        iv.layoutParams = lp
                    }
                }
                if (tv != null) {
                    tv.setTextColor(adaptiveColor)
                    tv.textSize = 10 * globalScale
                    tv.visibility = if (hideLabels) GONE else VISIBLE
                }

                val app = appMap[item.packageName]
                if (iv != null && app != null) {
                    appLoader.loadIcon(app, object : AppLoader.OnIconLoadedListener {
                        override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                            if (icon != null) {
                                iv.setImageBitmap(icon)
                                tv?.text = app.label
                            }
                        }
                    })
                }
            } else if (item.type == HomeItem.Type.FOLDER) {
                val tv = view.findViewWithTag<TextView>("item_label")
                if (tv != null) {
                    tv.setTextColor(adaptiveColor)
                    tv.textSize = 10 * globalScale
                    tv.visibility = if (hideLabels) GONE else VISIBLE
                    tv.text = if (item.folderName.isNullOrEmpty()) "" else item.folderName
                }
                if (context is MainActivity) {
                    val grid = view.findViewWithTag<android.widget.GridLayout>("folder_grid")
                    if (grid != null) {
                        (context as MainActivity).refreshFolderPreview(item, grid)
                    }
                }
            }
        }
    }

    fun refreshLayout() {
        post {
            val freeform = settingsManager.isFreeformHome
            for (page in pageManager.getAllPages()) {
                for (i in 0 until page.childCount) {
                    val v = page.getChildAt(i)
                    val item = v.tag as? HomeItem
                    if (item != null) {
                        if (!freeform) {
                            item.col = Math.max(0, Math.min(gridManager.columns - item.spanX.toInt(), Math.round(item.col))).toFloat()
                            item.row = Math.max(0, Math.min(gridManager.rows - item.spanY.toInt(), Math.round(item.row))).toFloat()
                            item.rotation = 0f
                            item.scaleX = 1.0f
                            item.scaleY = 1.0f
                            item.tiltX = 0f
                            item.tiltY = 0f
                        }
                        updateViewPosition(item, v)
                    }
                }
            }
            if (!freeform && context is MainActivity) {
                (context as MainActivity).saveHomeState()
            }
        }
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        pageIndicator.setAccentColor(color)
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val PAGE_SWITCH_COOLDOWN = 500L
        private const val HOLD_DELAY = 400L
        private const val EDGE_THRESHOLD = 0.12f
        private const val MIN_DRAG_DISTANCE_DP = 50
    }
}
