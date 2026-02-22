package com.riprog.launcher.ui.views.home

import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.callback.PageActionCallback
import com.riprog.launcher.R

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.ArrayList
import java.util.HashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class HomeView(context: Context) : FrameLayout(context), PageActionCallback {
    val pagesContainer: LinearLayout
    val pageIndicator: PageIndicator
    val pages: MutableList<FrameLayout> = ArrayList()
    private val settingsManager: SettingsManager = SettingsManager(context)
    private var systemTopInset = 0
    private var systemBottomInset = 0
    var currentPage: Int = 0
        private set
    private var accentColor = Color.WHITE
    private var model: AppRepository? = null
    private var allApps: List<AppItem>? = null

    private var draggingView: View? = null
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private val edgeScrollHandler = Handler(Looper.getMainLooper())
    private var isEdgeScrolling = false
    private var edgeHoldStart: Long = 0
    private val edgeScrollRunnable: Runnable = object : Runnable {
        override fun run() {
            if (draggingView != null) {
                if (lastX < width * 0.05f) {
                    if (currentPage > 0) {
                        scrollToPage(currentPage - 1)
                        edgeHoldStart = 0
                    } else {
                        handleEdgePageCreation()
                    }
                } else if (lastX > width * 0.95f) {
                    if (currentPage < pages.size - 1) {
                        scrollToPage(currentPage + 1)
                        edgeHoldStart = 0
                    } else {
                        handleEdgePageCreation()
                    }
                } else {
                    edgeHoldStart = 0
                }
                edgeScrollHandler.postDelayed(this, 400)
            } else {
                isEdgeScrolling = false
                edgeHoldStart = 0
            }
        }
    }

    fun addPageAtIndex(index: Int) {
        val page = FrameLayout(context)
        page.clipChildren = false
        page.clipToPadding = false
        pages.add(index, page)
        pagesContainer.addView(
            page, index, LinearLayout.LayoutParams(
                pageWidth, LayoutParams.MATCH_PARENT
            )
        )


        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v.tag as HomeItem?
                if (item != null) item.page = i
            }
        }
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

    init {
        clipChildren = false
        clipToPadding = false

        pagesContainer = LinearLayout(context)
        pagesContainer.orientation = LinearLayout.HORIZONTAL
        pagesContainer.setPadding(0, dpToPx(48), 0, 0)
        pagesContainer.clipChildren = false

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemTopInset = systemBars.top
            systemBottomInset = systemBars.bottom
            pagesContainer.setPadding(0, dpToPx(48) + systemTopInset, 0, 0)
            post { refreshLayout() }
            insets
        }
        pagesContainer.clipToPadding = false
        addView(pagesContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        pageIndicator = PageIndicator(context)
        val indicatorParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        indicatorParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        indicatorParams.bottomMargin = dpToPx(80)
        addView(pageIndicator, indicatorParams)


        addPage()
        addPage()

        addDrawerHint()
    }

    private fun addDrawerHint() {
        if (settingsManager.drawerOpenCount >= 5) return

        val hint = TextView(context)
        hint.text = context.getString(R.string.drawer_hint)
        hint.textSize = 12f
        hint.setTextColor(Color.GRAY and 0x80FFFFFF.toInt())
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
        page.clipChildren = false
        page.clipToPadding = false
        pages.add(page)
        pagesContainer.addView(
            page, LinearLayout.LayoutParams(
                pageWidth, LayoutParams.MATCH_PARENT
            )
        )
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

    private fun handleEdgePageCreation() {
        if (edgeHoldStart == 0L) {
            edgeHoldStart = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - edgeHoldStart > 1000) {
            if (lastX < width * 0.05f && currentPage == 0) {
                addPageAtIndex(0)
                scrollToPage(0)
            } else if (lastX > width * 0.95f && currentPage == pages.size - 1) {
                addPage()
                scrollToPage(pages.size - 1)
            }
            edgeHoldStart = 0
        }
    }

    fun addItemView(item: HomeItem?, view: View?) {
        if (item == null || view == null) return
        while (item.page >= pages.size) {
            addPage()
        }
        if (view.parent is ViewGroup) {
            (view.parent as ViewGroup).removeView(view)
        }
        val page = pages[item.page]

        updateViewPosition(item, view)
        view.tag = item
        page.addView(view)
    }

    fun getCellWidth(): Float {
        val horizontalPadding = dpToPx(HORIZONTAL_PADDING_DP) * 2
        val columnCount = settingsManager.columns
        return if (width > horizontalPadding) (width - horizontalPadding) / columnCount.toFloat() else 0f
    }

    fun getCellHeight(): Float {
        val topPadding = dpToPx(48)
        val bottomPadding = dpToPx(16)
        val dockHeight = 0
        val indicatorHeight = dpToPx(80)
        val systemInsets = systemTopInset + systemBottomInset
        val usableHeight = height - topPadding - bottomPadding - dockHeight - indicatorHeight - systemInsets

        val cw = getCellWidth()
        val ch = if (usableHeight > 0) usableHeight / GRID_ROWS.toFloat() else 0f

        return ch
    }

    fun updateViewPosition(item: HomeItem, view: View) {
        val cellWidth = getCellWidth()
        val cellHeight = getCellHeight()

        if (cellWidth <= 0f || cellHeight <= 0f) {
            post { updateViewPosition(item, view) }
            return
        }

        val lp = LayoutParams((cellWidth * item.spanX).toInt(), (cellHeight * item.spanY).toInt())
        view.layoutParams = lp

        val horizontalPadding = dpToPx(HORIZONTAL_PADDING_DP)
        view.x = item.col * cellWidth + horizontalPadding
        view.y = item.row * cellHeight

        view.rotation = item.rotation
        view.scaleX = item.scale
        view.scaleY = item.scale
        view.rotationX = item.tiltX
        view.rotationY = item.tiltY

        if (view is AppWidgetHostView) {
            val density = resources.displayMetrics.density
            val minW = (cellWidth * item.spanX / density).toInt()
            val minH = (cellHeight * item.spanY / density).toInt()
            view.updateAppWidgetSize(null, minW, minH, minW, minH)
        }
    }

    fun startDragging(v: View, x: Float, y: Float) {
        draggingView = v
        lastX = x
        lastY = y

        if (v.parent !== this) {
            var absX = v.x
            var absY = v.y
            val p = v.parent as View?
            if (p != null) {
                // If it was in MainLayout, we need absolute coordinates
                // But startExternalDrag already sets v.x/v.y in MainLayout coordinates
                (p as ViewGroup).removeView(v)
            }
            addView(v)
            v.x = absX
            v.y = absY
        }

        v.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.8f).setDuration(150).start()
        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    fun handleDrag(x: Float, y: Float) {
        if (draggingView != null) {
            val dx = x - lastX
            val dy = y - lastY
            draggingView!!.x = draggingView!!.x + dx
            draggingView!!.y = draggingView!!.y + dy
            lastX = x
            lastY = y
            checkEdgeScroll(x)

            val resolvedPage = resolvePageIndex(draggingView!!.x + draggingView!!.width / 2f)
            pageIndicator.setCurrentPage(resolvedPage)
        }
    }

    fun checkEdgeScroll(x: Float) {
        lastX = x
        if (x < width * 0.05f || x > width * 0.95f) {
            if (!isEdgeScrolling) {
                isEdgeScrolling = true
                edgeScrollHandler.postDelayed(edgeScrollRunnable, 300)
            }
        } else {
            isEdgeScrolling = false
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
        }
    }

    fun stopEdgeScroll() {
        isEdgeScrolling = false
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
    }

    fun endDragging() {
        if (draggingView != null) {
            draggingView!!.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
            val item = draggingView!!.tag as HomeItem?
            if (item != null) {
                val absX = draggingView!!.x
                val absY = draggingView!!.y

                val targetPage = resolvePageIndex(absX + draggingView!!.width / 2f)
                item.page = targetPage

                removeView(draggingView)
                addItemView(item, draggingView)

                draggingView!!.x = absX - (pages[targetPage].left + pagesContainer.translationX)
                draggingView!!.y = absY - (pages[targetPage].top + pagesContainer.translationY)

                snapToGrid(item, draggingView!!)
            }
            draggingView = null
            isEdgeScrolling = false
            edgeHoldStart = 0
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
            if (model != null && allApps != null) {
                refreshIcons(model!!, allApps!!)
            }
        }
    }

    fun cancelDragging() {
        draggingView = null
        isEdgeScrolling = false
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
    }

    fun removeItemView(item: HomeItem?) {
        if (item == null) return
        for (page in pages) {
            for (i in page.childCount - 1 downTo 0) {
                val child = page.getChildAt(i)
                if (child.tag === item) {
                    page.removeView(child)
                }
            }
        }
    }


    override fun onAddPage() {
        addPage()
        scrollToPage(pages.size - 1)
        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
    }

    override fun onRemovePage() {
        if (pages.size <= 1) return
        val index = currentPage
        removePage(index)
        if (currentPage >= pages.size) {
            currentPage = pages.size - 1
        }
        scrollToPage(currentPage)
        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
    }

    fun removePage(index: Int) {
        if (index < 0 || index >= pages.size) return

        if (context is MainActivity) {
            val activity = context as MainActivity
            activity.homeItems.removeAll { it.page == index }
            activity.homeItems.forEach {
                if (it.page > index) it.page--
            }
        }

        val page = pages.removeAt(index)
        pagesContainer.removeView(page)

        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v.tag as HomeItem?
                if (item != null) item.page = i
            }
        }
        pageIndicator.setPageCount(pages.size)
    }

    fun removeItemsByPackage(packageName: String?) {
        if (packageName == null) return
        for (page in pages) {
            for (i in page.childCount - 1 downTo 0) {
                val child = page.getChildAt(i)
                val item = child.tag as HomeItem?
                if (item != null && packageName == item.packageName) {
                    page.removeView(child)
                }
            }
        }
    }

    fun snapToGrid(item: HomeItem, v: View) {
        val cellWidth = getCellWidth()
        val cellHeight = getCellHeight()

        if (context is MainActivity) {
            val activity = context as MainActivity
            val midX = v.x + v.width / 2f
            val midY = v.y + v.height / 2f

            var otherView: View? = null
            val targetPageLayout = pages[item.page]
            for (i in 0 until targetPageLayout.childCount) {
                val child = targetPageLayout.getChildAt(i)
                if (child === v) continue
                if (midX >= child.x && midX <= child.x + child.width &&
                    midY >= child.y && midY <= child.y + child.height
                ) {
                    otherView = child
                    break
                }
            }

            if (otherView != null && item.type == HomeItem.Type.APP) {
                val otherItem = otherView.tag as HomeItem?
                if (otherItem != null) {
                    if (otherItem.type == HomeItem.Type.APP) {
                        activity.folderManager.mergeToFolder(otherItem, item, activity.homeItems)
                        return
                    } else if (otherItem.type == HomeItem.Type.FOLDER) {
                        activity.folderManager.addToFolder(otherItem, item, activity.homeItems)
                        return
                    }
                }
            }
        }

        if (settingsManager.isFreeformHome) {
            val horizontalPadding = dpToPx(HORIZONTAL_PADDING_DP)
            item.col = (v.x - horizontalPadding) / cellWidth
            item.row = v.y / cellHeight
            item.rotation = v.rotation
            item.scale = v.scaleX
            item.tiltX = v.rotationX
            item.tiltY = v.rotationY
        } else {
            val horizontalPadding = dpToPx(HORIZONTAL_PADDING_DP)
            var targetCol = ((v.x - horizontalPadding) / cellWidth).roundToInt()
            var targetRow = (v.y / cellHeight).roundToInt()

            if (!doesFit(item.spanX, item.spanY, targetCol, targetRow, item.page)) {
                val occupied = getOccupiedCells(item.page, item)
                val nearest = findNearestAvailable(occupied, targetRow, targetCol, item.spanX, item.spanY)
                if (nearest != null) {
                    targetRow = nearest.first
                    targetCol = nearest.second
                }
            }

            item.col = max(0, min(settingsManager.columns - item.spanX, targetCol)).toFloat()
            item.row = max(0, min(GRID_ROWS - item.spanY, targetRow)).toFloat()
            item.rotation = 0f
            item.scale = 1.0f
            item.tiltX = 0f
            item.tiltY = 0f

            v.animate()
                .x(item.col * cellWidth + horizontalPadding)
                .y(item.row * cellHeight)
                .setDuration(200)
                .start()
        }

        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
    }

    fun scrollToPage(page: Int) {
        if (page < 0 || page >= pages.size) return
        currentPage = page
        val targetX = page * width
        pagesContainer.animate()
            .translationX((-targetX).toFloat())
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                if (model != null && allApps != null) {
                    refreshIcons(model!!, allApps!!)
                }
            }
            .start()
        pageIndicator.setCurrentPage(page)
    }

    override fun getPageCount(): Int {
        return pages.size
    }

    fun refreshIcons(model: AppRepository, allApps: List<AppItem>) {
        this.model = model
        this.allApps = allApps
        val scale = settingsManager.iconScale
        val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val size = (baseSize * scale).toInt()

        val appMap = HashMap<String, AppItem>()
        for (a in allApps) {
            appMap[a.packageName] = a
        }

        for (page in pages) {
            for (i in 0 until page.childCount) {
                val view = page.getChildAt(i)
                val item = view.tag as HomeItem?
                if (item != null && item.type == HomeItem.Type.APP) {
                    if (view is ViewGroup) {
                        val container = view
                        val iv = findImageView(container)
                        val tv = findTextView(container)

                        if (iv != null) {
                            val lp = iv.layoutParams
                            if (lp.width != size) {
                                lp.width = size
                                lp.height = size
                                iv.layoutParams = lp
                            }
                        }
                        if (tv != null) {
                            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
                            tv.visibility = if (settingsManager.isHideLabels) View.GONE else View.VISIBLE
                        }

                        val app = appMap[item.packageName]

                        if (iv != null && app != null) {
                            val finalApp = app
                            model.loadIcon(app) { bitmap ->
                                if (bitmap != null) {
                                    iv.setImageBitmap(bitmap)
                                    if (tv != null) tv.text = finalApp.label
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findImageView(container: ViewGroup): ImageView? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is ImageView) {
                return child
            } else if (child is ViewGroup) {
                val iv = findImageView(child)
                if (iv != null) return iv
            }
        }
        return null
    }

    private fun findTextView(container: ViewGroup): TextView? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is TextView) {
                return child
            } else if (child is ViewGroup) {
                val tv = findTextView(child)
                if (tv != null) return tv
            }
        }
        return null
    }

    fun refreshLayout() {
        post {
            val freeform = settingsManager.isFreeformHome
            if (!freeform) {
                for (i in pages.indices) {
                    val page = pages[i]
                    for (j in 0 until page.childCount) {
                        val v = page.getChildAt(j)
                        val item = v.tag as HomeItem?
                        if (item != null) {
                            item.rotation = 0f
                            item.scale = 1.0f
                            item.tiltX = 0f
                            item.tiltY = 0f
                        }
                    }
                    resolveAllOverlaps(i)
                }
            } else {
                for (page in pages) {
                    for (i in 0 until page.childCount) {
                        val v = page.getChildAt(i)
                        val item = v.tag as HomeItem?
                        if (item != null) updateViewPosition(item, v)
                    }
                }
            }
            if (!freeform && context is MainActivity) {
                (context as MainActivity).saveHomeState()
            }
        }
    }

    private fun autoArrange(movedItem: HomeItem) {
        if (settingsManager.isFreeformHome) return
        val activity = context as? MainActivity ?: return
        val items = activity.homeItems
        val columns = settingsManager.columns

        val pageItems = items.filter { it.page == movedItem.page && it !== movedItem }

        val occupied = Array(GRID_ROWS) { BooleanArray(columns) }

        for (item in pageItems) {
            val rStart = max(0, item.row.roundToInt())
            val rEnd = min(GRID_ROWS - 1, rStart + item.spanY - 1)
            val cStart = max(0, item.col.roundToInt())
            val cEnd = min(columns - 1, cStart + item.spanX - 1)

            for (r in rStart..rEnd) {
                for (c in cStart..cEnd) {
                    if (r < GRID_ROWS && c < columns) occupied[r][c] = true
                }
            }
        }

        val overlappingItems = mutableListOf<HomeItem>()
        for (item in pageItems) {
            if (isOverlapping(movedItem, item)) {
                overlappingItems.add(item)
            }
        }

        if (overlappingItems.isNotEmpty()) {
            val finalOccupied = Array(GRID_ROWS) { BooleanArray(columns) }
            val mrStart = max(0, movedItem.row.roundToInt())
            val mrEnd = min(GRID_ROWS - 1, mrStart + movedItem.spanY - 1)
            val mcStart = max(0, movedItem.col.roundToInt())
            val mcEnd = min(columns - 1, mcStart + movedItem.spanX - 1)
            for (r in mrStart..mrEnd) {
                for (c in mcStart..mcEnd) {
                    if (r < GRID_ROWS && c < columns) finalOccupied[r][c] = true
                }
            }
            for (item in pageItems) {
                if (!overlappingItems.contains(item)) {
                    val rStart = max(0, item.row.roundToInt())
                    val rEnd = min(GRID_ROWS - 1, rStart + item.spanY - 1)
                    val cStart = max(0, item.col.roundToInt())
                    val cEnd = min(columns - 1, cStart + item.spanX - 1)
                    for (r in rStart..rEnd) {
                        for (c in cStart..cEnd) {
                            if (r < GRID_ROWS && c < columns) finalOccupied[r][c] = true
                        }
                    }
                }
            }

            for (item in overlappingItems) {
                val newPos = findNearestAvailable(finalOccupied, item.row.roundToInt(), item.col.roundToInt(), item.spanX, item.spanY)
                if (newPos != null) {
                    item.row = newPos.first.toFloat()
                    item.col = newPos.second.toFloat()
                    for (r in newPos.first until newPos.first + item.spanY) {
                        for (c in newPos.second until newPos.second + item.spanX) {
                            if (r < GRID_ROWS && c < columns) finalOccupied[r][c] = true
                        }
                    }
                    updateItemView(item)
                } else {
                    // No space on this page, move to next page
                    val v = findViewForItem(item)
                    if (v != null) {
                        item.page = item.page + 1
                        item.row = 0f
                        item.col = 0f
                        removeItemView(item)
                        addItemView(item, v)
                    }
                }
            }
        }
    }

    private fun resolveAllOverlaps(pageIndex: Int) {
        if (settingsManager.isFreeformHome) return
        val activity = context as? MainActivity ?: return
        val columns = settingsManager.columns
        val items = activity.homeItems.filter { it.page == pageIndex }.sortedBy { it.row * columns + it.col }

        val occupied = Array(GRID_ROWS) { BooleanArray(columns) }
        for (item in items) {
            var r = max(0, min(GRID_ROWS - item.spanY, item.row.roundToInt()))
            var c = max(0, min(columns - item.spanX, item.col.roundToInt()))

            if (!canPlace(occupied, r, c, item.spanX, item.spanY)) {
                val pos = findNearestAvailable(occupied, r, c, item.spanX, item.spanY)
                if (pos != null) {
                    r = pos.first
                    c = pos.second
                }
            }

            item.row = r.toFloat()
            item.col = c.toFloat()
            for (i in r until r + item.spanY) {
                for (j in c until c + item.spanX) {
                    if (i < GRID_ROWS && j < columns) occupied[i][j] = true
                }
            }
            updateItemView(item)
        }
    }

    private fun isOverlapping(a: HomeItem, b: HomeItem): Boolean {
        val al = a.col.roundToInt()
        val at = a.row.roundToInt()
        val ar = al + a.spanX
        val ab = at + a.spanY

        val bl = b.col.roundToInt()
        val bt = b.row.roundToInt()
        val br = bl + b.spanX
        val bb = bt + b.spanY

        return !(al >= br || ar <= bl || at >= bb || ab <= bt)
    }

    private fun findNearestAvailable(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        var minDest = Double.MAX_VALUE
        var bestPos: Pair<Int, Int>? = null
        val columns = settingsManager.columns

        for (i in 0..GRID_ROWS - spanY) {
            for (j in 0..columns - spanX) {
                if (canPlace(occupied, i, j, spanX, spanY)) {
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

    private fun canPlace(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int): Boolean {
        val columns = settingsManager.columns
        for (i in r until r + spanY) {
            for (j in c until c + spanX) {
                if (i >= GRID_ROWS || j >= columns || occupied[i][j]) return false
            }
        }
        return true
    }

    private fun findViewForItem(item: HomeItem): View? {
        for (page in pages) {
            for (i in 0 until page.childCount) {
                val v = page.getChildAt(i)
                if (v.tag === item) return v
            }
        }
        return null
    }

    private fun updateItemView(item: HomeItem) {
        val v = findViewForItem(item)
        if (v != null) updateViewPosition(item, v)
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        pageIndicator.setAccentColor(color)
    }

    fun resolvePageIndex(x: Float): Int {
        val scrollX = -pagesContainer.translationX
        val relativeX = x + scrollX
        val index = (relativeX / pageWidth).toInt()
        return max(0, min(pages.size - 1, index))
    }

    private val pageWidth: Int
        get() {
            var w = width
            if (w == 0) w = resources.displayMetrics.widthPixels
            return w
        }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    inner class PageIndicator(context: Context) : LinearLayout(context) {
        private var count = 0
        private var current = 0
        private var accentColor = Color.WHITE

        init {
            orientation = HORIZONTAL
        }

        fun setPageCount(count: Int) {
            this.count = count
            updateDots()
        }

        fun setCurrentPage(current: Int) {
            this.current = current
            updateDots()
        }

        fun setAccentColor(color: Int) {
            this.accentColor = color
            updateDots()
        }

        private fun updateDots() {
            removeAllViews()
            for (i in 0 until count) {
                val dot = View(context)
                val size = dpToPx(6)
                val lp = LayoutParams(size, size)
                lp.setMargins(dpToPx(4), 0, dpToPx(4), 0)
                dot.layoutParams = lp

                val shape = GradientDrawable()
                shape.shape = GradientDrawable.OVAL
                if (i == current) {
                    shape.setColor(accentColor)
                } else {
                    shape.setColor(Color.GRAY and 0x80FFFFFF.toInt())
                }
                dot.background = shape
                addView(dot)
            }
        }
    }

    fun getOccupiedCells(pageIndex: Int, excludeItem: HomeItem? = null): Array<BooleanArray> {
        val columns = settingsManager.columns
        val occupied = Array(GRID_ROWS) { BooleanArray(columns) }
        if (context is MainActivity) {
            val activity = context as MainActivity
            for (item in activity.homeItems) {
                if (item === excludeItem) continue
                if (item.page == pageIndex) {
                    val rStart = max(0, item.row.roundToInt())
                    val rEnd = min(GRID_ROWS - 1, rStart + item.spanY - 1)
                    val cStart = max(0, item.col.roundToInt())
                    val cEnd = min(columns - 1, cStart + item.spanX - 1)
                    for (r in rStart..rEnd) {
                        for (c in cStart..cEnd) {
                            if (r >= 0 && r < GRID_ROWS && c >= 0 && c < columns) occupied[r][c] = true
                        }
                    }
                }
            }
        }
        return occupied
    }

    fun isSpanValid(item: HomeItem, newSpanX: Int, newSpanY: Int, newCol: Int, newRow: Int): Boolean {
        val columns = settingsManager.columns
        if (newCol < 0 || newRow < 0 || newCol + newSpanX > columns || newRow + newSpanY > GRID_ROWS) return false
        val occupied = getOccupiedCells(item.page, item)
        return canPlace(occupied, newRow, newCol, newSpanX, newSpanY)
    }

    fun doesFit(spanX: Int, spanY: Int, col: Int, row: Int, pageIndex: Int): Boolean {
        val columns = settingsManager.columns
        if (col < 0 || row < 0 || col + spanX > columns || row + spanY > GRID_ROWS) return false
        val occupied = getOccupiedCells(pageIndex)
        return canPlace(occupied, row, col, spanX, spanY)
    }

    fun hasAnySpace(spanX: Int, spanY: Int, pageIndex: Int): Boolean {
        val columns = settingsManager.columns
        val occupied = getOccupiedCells(pageIndex)
        for (r in 0..GRID_ROWS - spanY) {
            for (c in 0..columns - spanX) {
                if (canPlace(occupied, r, c, spanX, spanY)) return true
            }
        }
        return false
    }

    companion object {
        const val GRID_COLUMNS = 4
        const val GRID_ROWS = 6
        const val HORIZONTAL_PADDING_DP = 16
    }
}
