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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.ui.adapters.HomePageAdapter
import java.util.ArrayList
import java.util.HashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class HomeView(context: Context) : FrameLayout(context), PageActionCallback {
    val recyclerView: RecyclerView
    val adapter: HomePageAdapter
    val pageIndicator: PageIndicator
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
                    if (currentPage < adapter.itemCount - 1) {
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
        if (context is MainActivity) {
            val activity = context as MainActivity
            activity.homeItems.forEach {
                if (it.page >= index) it.page++
            }
            refreshData(activity.homeItems)
            activity.saveHomeState()
        }
    }

    fun refreshData(items: List<HomeItem>) {
        val maxPage = items.maxOfOrNull { it.page } ?: 0
        val pagesList = mutableListOf<List<HomeItem>>()
        for (i in 0..maxPage) {
            pagesList.add(items.filter { it.page == i })
        }
        adapter.setPages(pagesList)
        pageIndicator.setPageCount(pagesList.size)
    }

    fun addPageLeft() {
        addPageAtIndex(0)
    }

    fun addPageRight() {
        addPage()
    }

    init {
        clipChildren = false
        clipToPadding = false

        recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        adapter = HomePageAdapter(context as MainActivity, settingsManager, object : HomePageAdapter.Callback {
            override fun onItemClick(item: HomeItem, view: View) {
                (context as MainActivity).handleItemClick(view)
            }

            override fun onItemLongClick(item: HomeItem, view: View): Boolean {
                (context as MainActivity).freeformInteraction.showTransformOverlay(view)
                return true
            }
        })
        recyclerView.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemTopInset = systemBars.top
            systemBottomInset = systemBars.bottom
            recyclerView.setPadding(0, dpToPx(48) + systemTopInset, 0, 0)
            insets
        }
        recyclerView.clipToPadding = false
        recyclerView.clipChildren = false
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = recyclerView.layoutManager as LinearLayoutManager
                    currentPage = lm.findFirstVisibleItemPosition()
                    pageIndicator.setCurrentPage(currentPage)
                }
            }
        })

        pageIndicator = PageIndicator(context)
        val indicatorParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        indicatorParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        indicatorParams.bottomMargin = dpToPx(80)
        addView(pageIndicator, indicatorParams)


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
        if (context is MainActivity) {
            val activity = context as MainActivity
            val maxPage = activity.homeItems.maxOfOrNull { it.page } ?: 0
            // We just ensure there's at least one more page than current max
            // Or we just add a page by ensuring maxPage + 1 exists in the grouping
            // Actually, addPage Right:
            addPageAtIndex(maxPage + 1)
        }
    }

    private fun handleEdgePageCreation() {
        if (edgeHoldStart == 0L) {
            edgeHoldStart = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - edgeHoldStart > 1000) {
            if (lastX < width * 0.05f && currentPage == 0) {
                addPageAtIndex(0)
                scrollToPage(0)
            } else if (lastX > width * 0.95f && currentPage == adapter.itemCount - 1) {
                addPage()
                scrollToPage(adapter.itemCount - 1)
            }
            edgeHoldStart = 0
        }
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

        return if (usableHeight > 0) usableHeight / GRID_ROWS.toFloat() else 0f
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

    fun cancelDragging() {
        draggingView = null
        isEdgeScrolling = false
        edgeHoldStart = 0
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
    }


    override fun onAddPage() {
        addPage()
        scrollToPage(adapter.itemCount - 1)
        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
    }

    override fun onRemovePage() {
        if (adapter.itemCount <= 1) return
        val index = currentPage
        removePage(index)
        if (currentPage >= adapter.itemCount) {
            currentPage = adapter.itemCount - 1
        }
        scrollToPage(currentPage)
        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
    }

    fun removePage(index: Int) {
        if (context is MainActivity) {
            val activity = context as MainActivity
            if (adapter.itemCount <= 1) return

            activity.homeItems.removeAll { it.page == index }
            activity.homeItems.forEach {
                if (it.page > index) it.page--
            }
            refreshData(activity.homeItems)
            activity.saveHomeState()
        }
    }

    fun removeItemsByPackage(packageName: String?) {
        if (packageName == null) return
        if (context is MainActivity) {
            val activity = context as MainActivity
            activity.homeItems.removeAll { it.packageName == packageName }
            refreshData(activity.homeItems)
            activity.saveHomeState()
        }
    }


    fun scrollToPage(page: Int) {
        recyclerView.smoothScrollToPosition(page)
        currentPage = page
        pageIndicator.setCurrentPage(page)
    }

    override fun getPageCount(): Int {
        return adapter.itemCount
    }

    private fun findViewForItem(item: HomeItem): View? {
        // Now handled by RV
        return null
    }

    private fun updateItemView(item: HomeItem) {
        // Now handled by RV
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


    fun setAccentColor(color: Int) {
        this.accentColor = color
        pageIndicator.setAccentColor(color)
    }

    fun resolvePageIndex(x: Float): Int {
        val lm = recyclerView.layoutManager as LinearLayoutManager
        return lm.findFirstVisibleItemPosition()
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
