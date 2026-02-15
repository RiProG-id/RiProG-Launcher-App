package com.riprog.launcher.ui

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
import android.widget.TextView
import com.riprog.launcher.MainActivity
import com.riprog.launcher.R
import com.riprog.launcher.model.AppItem
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.model.LauncherModel
import com.riprog.launcher.utils.SettingsManager
import com.riprog.launcher.utils.ThemeUtils

class HomeView(context: Context) : FrameLayout(context) {
    private val pagesContainer: LinearLayout = LinearLayout(context)
    private val pageIndicator: PageIndicator = PageIndicator(context)
    private val pages = mutableListOf<FrameLayout>()
    private val settingsManager: SettingsManager = SettingsManager(context)
    private var homeItems: List<HomeItem>? = null
    private var currentPage = 0
    private var accentColor = Color.WHITE
    private var model: LauncherModel? = null
    private var allApps: List<AppItem>? = null

    private val tempMatrix = Matrix()

    private var draggingView: View? = null
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
        addView(pagesContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val indicatorParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        indicatorParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        indicatorParams.bottomMargin = dpToPx(48)
        addView(pageIndicator, indicatorParams)

        val savedPageCount = settingsManager.pageCount
        for (i in 0 until savedPageCount) {
            addPage()
        }

        addDrawerHint()
        post { cleanupEmptyPages() }
    }

    fun checkEdgeScrollLoop(x: Float) {
        lastX = x
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPageSwitchTime < PAGE_SWITCH_COOLDOWN) return

        if (Math.abs(x - initialDragX) < dpToPx(MIN_DRAG_DISTANCE_DP)) return

        if (x < width * EDGE_THRESHOLD) {
            scrollToPage(currentPage - 1)
            lastPageSwitchTime = currentTime
        } else if (x > width * (1f - EDGE_THRESHOLD)) {
            scrollToPage(currentPage + 1)
            lastPageSwitchTime = currentTime
        }
    }

    fun addPageAtIndex(index: Int) {
        val page = FrameLayout(context)
        pages.add(index, page)
        pagesContainer.addView(page, index, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))

        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v.tag as? HomeItem
                item?.page = i
            }
        }
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

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

    fun addPage() {
        val page = FrameLayout(context)
        pages.add(page)
        pagesContainer.addView(page, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

    fun addItemView(item: HomeItem?, view: View?) {
        if (item == null || view == null) return

        val existing = findViewForItem(item)
        if (existing != null && existing !== view) {
            val parent = existing.parent as? ViewGroup
            parent?.removeView(existing)
        }

        while (item.page >= pages.size) {
            addPage()
        }
        val parent = view.parent as? ViewGroup
        parent?.removeView(view)

        val page = pages[item.page]
        view.tag = item
        page.addView(view)
        updateViewPosition(item, view)
    }

    fun updateViewPosition(item: HomeItem, view: View) {
        val availW = width - paddingLeft - paddingRight
        val availH = height - paddingTop - paddingBottom

        val cellWidth = if (availW > 0) availW / GRID_COLUMNS else 0
        val cellHeight = if (availH > 0) availH / GRID_ROWS else 0

        if (cellWidth <= 0 || cellHeight <= 0) {
            post { updateViewPosition(item, view) }
            return
        }

        val lp: LayoutParams
        if (item.type == HomeItem.Type.WIDGET || (item.type == HomeItem.Type.FOLDER && (item.spanX > 1.0f || item.spanY > 1.0f))) {
            lp = LayoutParams((cellWidth * item.spanX).toInt(), (cellHeight * item.spanY).toInt())
        } else {
            val size = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            lp = LayoutParams(size * 2, size * 2)
        }
        view.layoutParams = lp

        if (item.type == HomeItem.Type.APP || (item.type == HomeItem.Type.FOLDER && item.spanX <= 1.0f && item.spanY <= 1.0f)) {
            view.x = paddingLeft + item.col * cellWidth + (cellWidth - lp.width) / 2f
            view.y = paddingTop + item.row * cellHeight + (cellHeight - lp.height) / 2f
        } else {
            view.x = paddingLeft + item.col * cellWidth
            view.y = paddingTop + item.row * cellHeight
        }

        view.rotation = item.rotation
        view.scaleX = item.scaleX
        view.scaleY = item.scaleY
        view.rotationX = item.tiltX
        view.rotationY = item.tiltY
    }

    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    fun startDragging(v: View, x: Float, y: Float) {
        draggingView = v
        lastX = x
        lastY = y
        initialDragX = x
        val item = v.tag as? HomeItem
        if (item != null) initialPage = item.page

        val vPos = IntArray(2)
        v.getLocationOnScreen(vPos)
        dragOffsetX = x - vPos[0]
        dragOffsetY = y - vPos[1]

        if (context is MainActivity) {
            val root = v.rootView.findViewById<ViewGroup>(android.R.id.content)

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

        v.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.8f).setDuration(150).start()
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
            v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
            val item = v.tag as? HomeItem
            if (item != null) {
                snapToGrid(item, v)
            }
            draggingView = null
            isEdgeScrolling = false
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
            cleanupEmptyPages()
            if (model != null && allApps != null) {
                refreshIcons(model!!, allApps!!)
            }
        }
    }

    fun cancelDragging() {
        draggingView?.let { v ->
            v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
            val item = v.tag as? HomeItem
            if (item != null) {
                addItemView(item, v)
            }
        }
        draggingView = null
        isEdgeScrolling = false
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
    }

    fun cleanupEmptyPages() {
        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v?.tag as? HomeItem
                item?.page = i
            }
        }
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

    fun removePage(index: Int) {
        if (index < 0 || index >= pages.size || pages.size <= 1) return
        val oldPageCount = pages.size

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

        val page = pages.removeAt(index)
        pagesContainer.removeView(page)

        if (currentPage >= pages.size) {
            currentPage = pages.size - 1
        }

        cleanupEmptyPages()
        if (context is MainActivity) {
            settingsManager.removePageData(index, oldPageCount)
            (context as MainActivity).saveHomeState()
            scrollToPage(currentPage)
        }
    }

    fun removeItemsByPackage(packageName: String?) {
        if (packageName == null) return
        var changed = false
        for (page in pages) {
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
            cleanupEmptyPages()
        }
    }

    private fun findCollision(draggedView: View): HomeItem? {
        val draggedItem = draggedView.tag as? HomeItem ?: return null

        val currentPageLayout = pages[currentPage]
        val centerX = draggedView.x + draggedView.width / 2f
        val centerY = draggedView.y + draggedView.height / 2f

        for (i in 0 until currentPageLayout.childCount) {
            val child = currentPageLayout.getChildAt(i)
            if (child === draggedView) continue

            val targetItem = child.tag as? HomeItem ?: continue

            if (centerX >= child.x && centerX <= child.x + child.width &&
                centerY >= child.y && centerY <= child.y + child.height
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

        val cellWidth = if (availW > 0) availW / GRID_COLUMNS else 1
        val cellHeight = if (availH > 0) availH / GRID_ROWS else 1

        val coords = getRelativeCoords(v)
        val xInHome = coords[0] - paddingLeft
        val yInHome = coords[1] - paddingTop

        val oldPage = initialPage
        item.page = currentPage

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
            item.col = Math.max(0, Math.min(GRID_COLUMNS - item.spanX.toInt(), Math.round(xInHome / cellWidth.toFloat()))).toFloat()
            item.row = Math.max(0, Math.min(GRID_ROWS - item.spanY.toInt(), Math.round(yInHome / cellHeight.toFloat()))).toFloat()
            item.rotation = 0f
            item.scaleX = 1.0f
            item.scaleY = 1.0f
            item.tiltX = 0f
            item.tiltY = 0f
        }

        addItemView(item, v)

        if (context is MainActivity) {
            if (oldPage != -1 && oldPage != item.page) {
                (context as MainActivity).savePage(oldPage)
            }
            (context as MainActivity).savePage(item.page)
        }
    }

    fun scrollToPage(page: Int) {
        if (pages.size <= 1) {
            currentPage = 0
            pagesContainer.translationX = 0f
            pageIndicator.setCurrentPage(0)
            return
        }

        val n = pages.size

        if (page == n && currentPage == n - 1) {
            val p0 = pages[0]
            pagesContainer.removeView(p0)
            pagesContainer.addView(p0)
            pagesContainer.translationX = (-(n - 2) * width).toFloat()

            pagesContainer.animate()
                .translationX((-(n - 1) * width).toFloat())
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    pagesContainer.removeView(p0)
                    pagesContainer.addView(p0, 0)
                    pagesContainer.translationX = 0f
                    currentPage = 0
                    pageIndicator.setCurrentPage(0)
                    refreshIconsDebounced()
                }.start()
            return
        } else if (page == -1 && currentPage == 0) {
            val pLast = pages[n - 1]
            pagesContainer.removeView(pLast)
            pagesContainer.addView(pLast, 0)
            pagesContainer.translationX = (-width).toFloat()

            pagesContainer.animate()
                .translationX(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    pagesContainer.removeView(pLast)
                    pagesContainer.addView(pLast)
                    pagesContainer.translationX = (-(n - 1) * width).toFloat()
                    currentPage = n - 1
                    pageIndicator.setCurrentPage(n - 1)
                    refreshIconsDebounced()
                }.start()
            return
        }

        val targetPage = (page + n) % n
        currentPage = targetPage
        val targetX = currentPage * width
        pagesContainer.animate()
            .translationX((-targetX).toFloat())
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                refreshIconsDebounced()
            }
            .start()
        pageIndicator.setCurrentPage(currentPage)
    }

    fun getCurrentPage(): Int = currentPage

    fun getPageCount(): Int = pages.size

    private val refreshIconsRunnable = Runnable {
        if (model != null && allApps != null) {
            refreshIconsInternal(model!!, allApps!!)
        }
    }

    fun refreshIconsDebounced() {
        mainHandler.removeCallbacks(refreshIconsRunnable)
        mainHandler.postDelayed(refreshIconsRunnable, 100)
    }

    fun setHomeItems(items: List<HomeItem>) {
        this.homeItems = items
    }

    fun refreshIcons(model: LauncherModel, allApps: List<AppItem>) {
        this.model = model
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
        for (pageLayout in pages) {
            for (i in 0 until pageLayout.childCount) {
                val v = pageLayout.getChildAt(i)
                if (v.tag === item) return v
            }
        }
        return null
    }

    private fun refreshIconsInternal(model: LauncherModel, allApps: List<AppItem>) {
        val globalScale = if (settingsManager.isFreeformHome) 1.0f else settingsManager.iconScale
        val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val targetIconSize = (baseSize * globalScale).toInt()
        val hideLabels = settingsManager.isHideLabels

        val appMap = mutableMapOf<String, AppItem>()
        for (a in allApps) {
            appMap[a.packageName] = a
        }

        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, false)

        if (currentPage in pages.indices) {
            refreshPageIcons(pages[currentPage], model, appMap, targetIconSize, globalScale, hideLabels, adaptiveColor)
        }

        post {
            for (i in pages.indices) {
                if (i == currentPage) continue
                refreshPageIcons(pages[i], model, appMap, targetIconSize, globalScale, hideLabels, adaptiveColor)
            }
        }
    }

    private fun refreshPageIcons(
        page: FrameLayout, model: LauncherModel, appMap: Map<String, AppItem>,
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
                    model.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
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
            for (page in pages) {
                for (i in 0 until page.childCount) {
                    val v = page.getChildAt(i)
                    val item = v.tag as? HomeItem
                    if (item != null) {
                        if (!freeform) {
                            item.col = Math.max(0, Math.min(GRID_COLUMNS - item.spanX.toInt(), Math.round(item.col))).toFloat()
                            item.row = Math.max(0, Math.min(GRID_ROWS - item.spanY.toInt(), Math.round(item.row))).toFloat()
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
        const val GRID_COLUMNS = 4
        const val GRID_ROWS = 6
        private const val PAGE_SWITCH_COOLDOWN = 1000L
        private const val HOLD_DELAY = 1000L
        private const val EDGE_THRESHOLD = 0.05f
        private const val MIN_DRAG_DISTANCE_DP = 50
    }
}
