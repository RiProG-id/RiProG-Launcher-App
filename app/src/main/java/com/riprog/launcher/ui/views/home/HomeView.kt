package com.riprog.launcher.ui.views.home

import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.callback.PageActionCallback
import com.riprog.launcher.logic.utils.WidgetSizingUtils
import com.riprog.launcher.R

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.RectF
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
import java.util.ArrayList
import java.util.HashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class HomeView(context: Context) : FrameLayout(context), PageActionCallback {
    val recyclerView: RecyclerView
    val adapter: HomePagerAdapter
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
            var actContext = context
            while (actContext !is MainActivity && actContext is ContextWrapper) {
                actContext = actContext.baseContext
            }
            val activity = actContext as? MainActivity
            val isTransforming = activity?.freeformInteraction?.isTransforming() == true

            if (isEdgeScrolling && (draggingView != null || isTransforming)) {
                if (lastX < width * 0.10f) {
                    if (currentPage > 0) {
                        scrollToPage(currentPage - 1)
                        edgeHoldStart = 0
                    } else {
                        handleEdgePageCreation()
                    }
                } else if (lastX > width * 0.90f) {
                    if (currentPage < pages.size - 1) {
                        scrollToPage(currentPage + 1)
                        edgeHoldStart = 0
                    } else {
                        handleEdgePageCreation()
                    }
                } else {
                    edgeHoldStart = 0
                    stopEdgeEffect()
                }
                edgeScrollHandler.postDelayed(this, 1000)
            } else {
                isEdgeScrolling = false
                edgeHoldStart = 0
                stopEdgeEffect()
            }
        }
    }

    fun addPageAtIndex(index: Int) {
        val page = FrameLayout(context)
        page.clipChildren = false
        page.clipToPadding = false
        pages.add(index, page)
        settingsManager.pageCount = pages.size

        if (context is MainActivity) {
            val activity = context as MainActivity
            activity.homeItems.forEach {
                if (it.page >= index) it.page++
            }
        }

        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v.tag as HomeItem?
                if (item != null) item.page = i
            }
        }

        adapter.notifyItemInserted(index)
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)

        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
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
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.layoutManager = layoutManager
        recyclerView.setPadding(0, dpToPx(32), 0, 0)
        recyclerView.clipChildren = false
        recyclerView.clipToPadding = false

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val pos = layoutManager.findFirstCompletelyVisibleItemPosition()
                    if (pos != RecyclerView.NO_POSITION) {
                        currentPage = pos
                        pageIndicator.setCurrentPage(currentPage)
                        if (model != null && allApps != null) {
                            refreshIcons(model!!, allApps!!)
                        }
                    }
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemTopInset = systemBars.top
            systemBottomInset = systemBars.bottom
            recyclerView.setPadding(0, dpToPx(32) + systemTopInset, 0, 0)

            val indicatorParams = pageIndicator.layoutParams as LayoutParams
            indicatorParams.bottomMargin = systemBottomInset + dpToPx(8)
            pageIndicator.layoutParams = indicatorParams

            post { refreshLayout() }
            insets
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        adapter = HomePagerAdapter()
        recyclerView.adapter = adapter

        pageIndicator = PageIndicator(context)
        pageIndicator.isClickable = false
        pageIndicator.isFocusable = false
        val indicatorParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        indicatorParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        indicatorParams.bottomMargin = dpToPx(80)
        addView(pageIndicator, indicatorParams)

        val pc = settingsManager.pageCount
        for (i in 0 until pc) {
            addPage()
        }

        addDrawerHint()
    }

    private fun addDrawerHint() {
        if (settingsManager.drawerOpenCount >= 5) return

        val hint = TextView(context)
        hint.isClickable = false
        hint.isFocusable = false
        hint.text = context.getString(R.string.drawer_hint)
        hint.textSize = 12f
        hint.setTextColor(Color.GRAY and 0x80FFFFFF.toInt())
        hint.alpha = 0f
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.bottomMargin = dpToPx(120)
        addView(hint, lp)

        if (Math.random() < 0.3 && !settingsManager.isAcrylic) {
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
        settingsManager.pageCount = pages.size
        adapter.notifyItemInserted(pages.size - 1)
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

    private fun handleEdgePageCreation() {
        if (edgeHoldStart == 0L) {
            edgeHoldStart = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - edgeHoldStart > 1000) {
            if (lastX < width * 0.10f && currentPage == 0) {
                addPageAtIndex(0)
                scrollToPage(0)
            } else if (lastX > width * 0.90f && currentPage == pages.size - 1) {
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

        // Synchronize visual state to avoid data drift/re-render loops
        item.originalPage = item.page

        // Ensure last interaction / last placed item is on top (Z-order)
        view.bringToFront()

        // Initial add flow for all items in Non-Freeform mode:
        // Wait until rendered, then detect actual visual span and lock position.
        if (!settingsManager.isFreeformHome && item.visualOffsetX < 0) {
            view.post {
                snapToGrid(item, view)
            }
        }
    }

    fun getCellWidth(): Float {
        val horizontalPadding = dpToPx(HORIZONTAL_PADDING_DP) * 2
        val columnCount = settingsManager.columns
        return if (width > horizontalPadding) (width - horizontalPadding) / columnCount.toFloat() else 0f
    }

    fun getCellHeight(): Float {
        return getCellWidth()
    }

    fun getSnapPosition(item: HomeItem, view: View): Pair<Float, Float> {
        val cellWidth = getCellWidth()
        val cellHeight = getCellHeight()
        val horizontalPadding = dpToPx(HORIZONTAL_PADDING_DP)

        if (settingsManager.isFreeformHome) {
            return Pair(item.col * cellWidth + horizontalPadding, item.row * cellHeight)
        } else {
            val vBounds = WidgetSizingUtils.getVisualBounds(view)
            // Use stored offsets to ensure pixel-perfect locked position after save/restore
            val vCenterX = if (item.visualOffsetX >= 0) item.visualOffsetX else if (vBounds.width() > 0) vBounds.centerX() else (item.spanX * cellWidth) / 2f
            val vCenterY = if (item.visualOffsetY >= 0) item.visualOffsetY else if (vBounds.height() > 0) vBounds.centerY() else (item.spanY * cellHeight) / 2f

            val targetX = (item.col + item.spanX / 2f) * cellWidth + horizontalPadding - vCenterX
            val targetY = (item.row + item.spanY / 2f) * cellHeight - vCenterY
            return Pair(targetX, targetY)
        }
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

        val pos = getSnapPosition(item, view)
        view.x = pos.first
        view.y = pos.second

        view.rotation = item.rotation
        view.scaleX = item.scale
        view.scaleY = item.scale
        view.rotationX = item.tiltX
        view.rotationY = item.tiltY

        if (view is AppWidgetHostView) {
            val density = resources.displayMetrics.density
            val minW = (cellWidth * item.spanX / density).toInt()
            val minH = (cellHeight * item.spanY / density).toInt()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minW)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minH)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH)
            }
            view.updateAppWidgetOptions(options)
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
                // If it was in a page (which is now in RecyclerView), we need to account for its position
                val pageLoc = IntArray(2)
                (p as View).getLocationInWindow(pageLoc)
                val homeLoc = IntArray(2)
                this.getLocationInWindow(homeLoc)

                absX += pageLoc[0] - homeLoc[0]
                absY += pageLoc[1] - homeLoc[1]

                (p as ViewGroup).removeView(v)
            }
            addView(v)
            v.x = absX
            v.y = absY
        }

        if (settingsManager.isAcrylic) {
            v.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.8f).setDuration(150).start()
        } else {
            v.scaleX = 1.1f
            v.scaleY = 1.1f
            v.alpha = 0.8f
        }
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

            val resolvedPage = resolvePageIndex(x)
            pageIndicator.setCurrentPage(resolvedPage)
        }
    }

    fun checkEdgeScroll(x: Float) {
        lastX = x
        if (x < width * 0.10f || x > width * 0.90f) {
            if (!isEdgeScrolling) {
                isEdgeScrolling = true
                edgeScrollHandler.postDelayed(edgeScrollRunnable, 400)
                animateEdgeEffect(x < width * 0.5f)
            }
        } else {
            isEdgeScrolling = false
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
            stopEdgeEffect()
        }
    }

    private fun animateEdgeEffect(isLeft: Boolean) {
        val shift = if (isLeft) width * 0.05f else -width * 0.05f
        recyclerView.animate()
            .translationX(shift)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun stopEdgeEffect() {
        recyclerView.animate()
            .translationX(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()
    }

    fun stopEdgeScroll() {
        isEdgeScrolling = false
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
        stopEdgeEffect()
    }

    fun endDragging() {
        if (draggingView != null) {
            val v = draggingView!!
            if (settingsManager.isAcrylic) {
                v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
            } else {
                v.scaleX = 1.0f
                v.scaleY = 1.0f
                v.alpha = 1.0f
            }
            val item = v.tag as HomeItem?
            if (item != null) {
                val absXInWindow = IntArray(2).apply { v.getLocationInWindow(this) }[0]
                val absYInWindow = IntArray(2).apply { v.getLocationInWindow(this) }[1]

                val targetPage = resolvePageIndex(v.x + v.width / 2f)
                item.page = targetPage

                removeView(v)

                if (context is MainActivity) {
                    val activity = context as MainActivity
                    pageIndicator.setCurrentPage(targetPage)

                    if (!activity.homeItems.contains(item)) {
                        activity.homeItems.add(item)
                    }

                    val newView = activity.renderHomeItem(item)
                    if (newView != null) {
                        val pageLoc = IntArray(2)
                        // We need to wait for the page to be bound/laid out if it's not visible
                        recyclerView.scrollToPosition(targetPage)
                        recyclerView.post {
                            val holder = recyclerView.findViewHolderForAdapterPosition(targetPage) as? HomePagerAdapter.ViewHolder
                            if (holder != null) {
                                holder.container.getLocationInWindow(pageLoc)
                                newView.x = absXInWindow - pageLoc[0].toFloat()
                                newView.y = absYInWindow - pageLoc[1].toFloat()
                                snapToGrid(item, newView)
                            } else {
                                // Fallback if holder is not available
                                snapToGrid(item, newView)
                            }
                        }
                    }
                    activity.saveHomeState()
                }
            }
            cleanupDraggingState()
            if (model != null && allApps != null) {
                refreshIcons(model!!, allApps!!)
            }
        }
    }

    private fun cleanupDraggingState() {
        draggingView = null
        isEdgeScrolling = false
        edgeHoldStart = 0
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
        stopEdgeEffect()
    }

    fun cancelDragging() {
        draggingView = null
        isEdgeScrolling = false
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
        stopEdgeEffect()
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
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child.tag === item) {
                removeView(child)
            }
        }
        val parentGroup = parent as? ViewGroup
        if (parentGroup != null) {
            for (i in parentGroup.childCount - 1 downTo 0) {
                val child = parentGroup.getChildAt(i)
                if (child.tag === item) {
                    parentGroup.removeView(child)
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

        pages.removeAt(index)
        settingsManager.pageCount = pages.size
        adapter.notifyItemRemoved(index)

        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v.tag as HomeItem?
                if (item != null) item.page = i
            }
        }
        pageIndicator.setPageCount(pages.size)

        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
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

    fun snapToGrid(item: HomeItem, v: View): Boolean {
        val cellWidth = getCellWidth()
        val cellHeight = getCellHeight()
        val vBounds = WidgetSizingUtils.getVisualBounds(v)

        if (context is MainActivity) {
            val activity = context as MainActivity
            val midX = v.x + vBounds.centerX()
            val midY = v.y + vBounds.centerY()

            var otherView: View? = null
            val targetPageLayout = if (item.page < pages.size) pages[item.page] else null
            if (targetPageLayout != null) {
                for (i in 0 until targetPageLayout.childCount) {
                    val child = targetPageLayout.getChildAt(i)
                    if (child === v) continue

                    val otherItem = child.tag as? HomeItem
                    if (otherItem != null) {
                        if (areOverlappingVisually(item, v, otherItem, child)) {
                            otherView = child
                            break
                        }
                    }
                }
            }

            if (!settingsManager.isFreeformHome && otherView != null && item.type == HomeItem.Type.APP && otherView.parent != null) {
                val otherItem = otherView.tag as HomeItem?
                if (otherItem != null && otherItem !== item) {
                    if (otherItem.type == HomeItem.Type.APP) {
                        activity.folderManager.mergeToFolder(otherItem, item, activity.homeItems)
                        return true
                    } else if (otherItem.type == HomeItem.Type.FOLDER) {
                        activity.folderManager.addToFolder(otherItem, item, activity.homeItems)
                        return true
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

            // 1. Calculate visual span (EXACT)
            val sX: Int
            val sY: Int
            if (item.type == HomeItem.Type.WIDGET) {
                sX = (vBounds.width() / cellWidth).roundToInt().coerceAtLeast(1)
                sY = (vBounds.height() / cellHeight).roundToInt().coerceAtLeast(1)
            } else {
                sX = 1
                sY = 1
            }

            // Store FINAL visual offsets as single source of truth to lock position and prevent post-save shifts
            item.visualOffsetX = vBounds.centerX()
            item.visualOffsetY = vBounds.centerY()

            // 2. Scan grid behind object position for nearest valid grid area matching EXACT span
            val visualCenterX = v.x + vBounds.centerX()
            val visualCenterY = v.y + vBounds.centerY()

            var targetCol = ((visualCenterX - horizontalPadding - (cellWidth * sX / 2f)) / cellWidth).roundToInt()
            var targetRow = ((visualCenterY - (cellHeight * sY / 2f)) / cellHeight).roundToInt()

            targetCol = targetCol.coerceIn(0, settingsManager.columns - sX)
            targetRow = targetRow.coerceIn(0, GRID_ROWS - sY)

            // 3. Apply SNAP RESULT and handle overlaps
            applyNewGridLogic(item, v, targetCol, targetRow, sX, sY)

            val pageChanged = item.page != item.originalPage
            if (pageChanged && context is MainActivity) {
                removeItemView(item)
                (context as MainActivity).renderHomeItem(item)
                return false
            }
            item.rotation = 0f
            item.scale = 1.0f
            item.tiltX = 0f
            item.tiltY = 0f

            if (settingsManager.isAcrylic) {
                val pos = getSnapPosition(item, v)
                v.animate()
                    .x(pos.first)
                    .y(pos.second)
                    .setDuration(200)
                    .withEndAction { updateViewPosition(item, v) }
                    .start()
            } else {
                updateViewPosition(item, v)
            }
        }

        if (context is MainActivity) {
            (context as MainActivity).saveHomeState()
        }
        return false
    }

    private fun getVisualArea(item: HomeItem, view: View?): Float {
        if (view != null) {
            val bounds = WidgetSizingUtils.getVisualBounds(view)
            return bounds.width() * bounds.height()
        }
        val cw = getCellWidth()
        val ch = getCellHeight()
        return (item.spanX * cw) * (item.spanY * ch)
    }

    private fun getAbsoluteVisualBounds(item: HomeItem, view: View): RectF {
        val vBounds = WidgetSizingUtils.getVisualBounds(view)
        val cellWidth = getCellWidth()
        val cellHeight = getCellHeight()
        val horizontalPadding = dpToPx(HORIZONTAL_PADDING_DP)

        val vCenterX = if (item.visualOffsetX >= 0) item.visualOffsetX else if (vBounds.width() > 0) vBounds.centerX() else (item.spanX * cellWidth) / 2f
        val vCenterY = if (item.visualOffsetY >= 0) item.visualOffsetY else if (vBounds.height() > 0) vBounds.centerY() else (item.spanY * cellHeight) / 2f

        val targetX = (item.col + item.spanX / 2f) * cellWidth + horizontalPadding - vCenterX
        val targetY = (item.row + item.spanY / 2f) * cellHeight - vCenterY

        return RectF(
            targetX + vBounds.left,
            targetY + vBounds.top,
            targetX + vBounds.right,
            targetY + vBounds.bottom
        )
    }

    private fun areOverlappingVisually(item1: HomeItem, view1: View, item2: HomeItem, view2: View): Boolean {
        val b1 = getAbsoluteVisualBounds(item1, view1)
        val b2 = getAbsoluteVisualBounds(item2, view2)
        return RectF.intersects(b1, b2)
    }

    fun applyNewGridLogic(droppedItem: HomeItem, droppedView: View, targetCol: Int, targetRow: Int, spanX: Int, spanY: Int) {
        val activity = context as? MainActivity ?: return

        val oldCol = droppedItem.col
        val oldRow = droppedItem.row
        val oldPage = droppedItem.page
        val oldSpanX = droppedItem.spanX
        val oldSpanY = droppedItem.spanY

        // Temporarily set dropped item at target position to check for overlaps
        droppedItem.col = targetCol.toFloat()
        droppedItem.row = targetRow.toFloat()
        droppedItem.spanX = spanX.toFloat()
        droppedItem.spanY = spanY.toFloat()

        val droppedArea = getVisualArea(droppedItem, droppedView)

        // Items on the same page, excluding the one we just dropped
        val pageItems = activity.homeItems.filter { it.page == droppedItem.page && it !== droppedItem }

        // Detect all visual overlaps
        val overlaps = pageItems.filter { other ->
            val otherView = findViewForItem(other)
            if (otherView != null) {
                areOverlappingVisually(droppedItem, droppedView, other, otherView)
            } else {
                intersects(droppedItem, other)
            }
        }

        if (overlaps.isNotEmpty()) {
            val firstOther = overlaps[0]
            val otherArea = getVisualArea(firstOther, findViewForItem(firstOther))

            if (overlaps.size == 1 && kotlin.math.abs(droppedArea - otherArea) < 0.1f) {
                // CASE 2: Same size -> Swap
                if (oldPage == droppedItem.page && oldCol >= 0 && oldRow >= 0) {
                    firstOther.col = oldCol
                    firstOther.row = oldRow
                    firstOther.spanX = oldSpanX
                    firstOther.spanY = oldSpanY
                    // Re-capture offsets for center locking
                    val otherView = findViewForItem(firstOther)
                    if (otherView != null) {
                        val vBounds = WidgetSizingUtils.getVisualBounds(otherView)
                        firstOther.visualOffsetX = vBounds.centerX()
                        firstOther.visualOffsetY = vBounds.centerY()
                    }
                    updateItemView(firstOther)
                } else {
                    moveItemToNearestEmpty(firstOther, activity.homeItems)
                }
            } else {
                // CASE 1: Different sizes or multiple overlaps
                val existsLarger = overlaps.any { getVisualArea(it, findViewForItem(it)) > droppedArea + 0.1f }

                if (existsLarger) {
                    // droppedItem is smaller than at least one item it overlaps -> droppedItem moves
                    val nearest = findNearestEmptyArea(droppedItem.page, spanX, spanY, targetCol, targetRow, pageItems)
                    if (nearest != null) {
                        droppedItem.col = nearest.first.toFloat()
                        droppedItem.row = nearest.second.toFloat()
                    }
                } else {
                    // droppedItem is larger than all items it overlaps -> all overlapping items move
                    for (other in overlaps) {
                        moveItemToNearestEmpty(other, activity.homeItems)
                    }
                }
            }
        }
    }

    private fun moveItemToNearestEmpty(item: HomeItem, allItems: List<HomeItem>) {
        val nearest = findNearestEmptyArea(item.page, item.spanX.roundToInt(), item.spanY.roundToInt(), item.col.roundToInt(), item.row.roundToInt(), allItems.filter { it !== item })
        if (nearest != null) {
            item.col = nearest.first.toFloat()
            item.row = nearest.second.toFloat()
            updateItemView(item)
        }
    }

    private fun intersects(item1: HomeItem, item2: HomeItem): Boolean {
        val r1 = item1.row.roundToInt()
        val c1 = item1.col.roundToInt()
        val sX1 = item1.spanX.roundToInt()
        val sY1 = item1.spanY.roundToInt()

        val r2 = item2.row.roundToInt()
        val c2 = item2.col.roundToInt()
        val sX2 = item2.spanX.roundToInt()
        val sY2 = item2.spanY.roundToInt()

        return c1 < c2 + sX2 && c1 + sX1 > c2 && r1 < r2 + sY2 && r1 + sY1 > r2
    }

    private fun findNearestEmptyArea(pageIndex: Int, spanX: Int, spanY: Int, prefCol: Int, prefRow: Int, otherItems: List<HomeItem>): Pair<Int, Int>? {
        val cols = settingsManager.columns
        val occupied = Array(GRID_ROWS) { BooleanArray(cols) }

        for (item in otherItems) {
            if (item.page == pageIndex) {
                val rStart = max(0, item.row.roundToInt())
                val rEnd = min(GRID_ROWS - 1, (item.row + item.spanY).roundToInt() - 1)
                val cStart = max(0, item.col.roundToInt())
                val cEnd = min(cols - 1, (item.col + item.spanX).roundToInt() - 1)
                for (r in rStart..rEnd) {
                    for (c in cStart..cEnd) {
                        occupied[r][c] = true
                    }
                }
            }
        }

        var minDest = Double.MAX_VALUE
        var bestPos: Pair<Int, Int>? = null

        for (i in 0..GRID_ROWS - spanY) {
            for (j in 0..cols - spanX) {
                var canPlace = true
                for (ri in i until i + spanY) {
                    for (ci in j until j + spanX) {
                        if (occupied[ri][ci]) {
                            canPlace = false
                            break
                        }
                    }
                    if (!canPlace) break
                }

                if (canPlace) {
                    val d = Math.sqrt(Math.pow((i - prefRow).toDouble(), 2.0) + Math.pow((j - prefCol).toDouble(), 2.0))
                    if (d < minDest) {
                        minDest = d
                        bestPos = Pair(j, i) // Note: returning (col, row)
                    }
                }
            }
        }
        return bestPos
    }

    fun scrollToPage(page: Int) {
        if (page < 0 || page >= pages.size) return
        currentPage = page
        recyclerView.smoothScrollToPosition(page)
        pageIndicator.setCurrentPage(page)
        stopEdgeEffect()
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
                        val iv = findImageView(view)
                        val tv = findTextView(view)

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
            for (i in pages.indices) {
                val page = pages[i]
                for (j in 0 until page.childCount) {
                    val v = page.getChildAt(j)
                    val item = v.tag as HomeItem? ?: continue

                    if (!freeform) {
                        // Reset transforms during conversion or refresh in grid mode
                        item.rotation = 0f
                        item.scale = 1.0f
                        item.tiltX = 0f
                        item.tiltY = 0f

                        // Ensure it's snapped to grid if not already
                        val colInt = item.col.roundToInt().toFloat()
                        val rowInt = item.row.roundToInt().toFloat()
                        if (item.col != colInt || item.row != rowInt || item.visualOffsetX < 0) {
                            snapToGrid(item, v)
                        } else {
                            updateViewPosition(item, v)
                        }
                    } else {
                        updateViewPosition(item, v)
                    }
                }
            }
            if (context is MainActivity) {
                (context as MainActivity).saveHomeState()
            }
        }
    }

    private fun resolveAllOverlaps(pageIndex: Int) {
        // Disabled per requirements: Object locks permanently, no secondary auto layout.
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

    private val pageWidth: Int
        get() {
            var w = width
            if (w == 0) w = resources.displayMetrics.widthPixels
            return w
        }

    fun resolvePageIndex(x: Float): Int {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return 0
        val first = lm.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return 0

        val firstView = lm.findViewByPosition(first) ?: return 0
        val pageW = if (firstView.width > 0) firstView.width else pageWidth
        if (pageW <= 0) return 0

        // Account for edge preview translation
        val adjustedX = x - recyclerView.translationX

        // Use scrollOffset calculation for better precision
        val scrollX = -firstView.left + first * pageW
        val relativeX = adjustedX + scrollX
        val index = floor((relativeX / pageW).toDouble()).toInt()
        return index.coerceIn(0, pages.size - 1)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    inner class HomePagerAdapter : RecyclerView.Adapter<HomePagerAdapter.ViewHolder>() {
        inner class ViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val frame = FrameLayout(parent.context)
            frame.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            frame.clipChildren = false
            frame.clipToPadding = false
            return ViewHolder(frame)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val page = pages[position]
            if (page.parent != null) (page.parent as ViewGroup).removeView(page)
            holder.container.removeAllViews()
            holder.container.addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        override fun onViewRecycled(holder: ViewHolder) {
            holder.container.removeAllViews()
        }

        override fun getItemCount(): Int = pages.size
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
                    val rStart = max(0, floor(item.row.toDouble() + 0.01).toInt())
                    val rEnd = min(GRID_ROWS - 1, ceil((item.row + item.spanY).toDouble() - 0.01).toInt() - 1)
                    val cStart = max(0, floor(item.col.toDouble() + 0.01).toInt())
                    val cEnd = min(columns - 1, ceil((item.col + item.spanX).toDouble() - 0.01).toInt() - 1)
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

    fun isSpanValid(item: HomeItem, newSpanX: Float, newSpanY: Float, newCol: Int, newRow: Int): Boolean {
        val columns = settingsManager.columns
        if (newCol < 0 || newRow < 0 || newCol + newSpanX.roundToInt() > columns || newRow + newSpanY.roundToInt() > GRID_ROWS) return false
        val occupied = getOccupiedCells(item.page, item)
        return canPlace(occupied, newRow, newCol, newSpanX.roundToInt(), newSpanY.roundToInt())
    }

    fun doesFit(spanX: Float, spanY: Float, col: Int, row: Int, pageIndex: Int, excludeItem: HomeItem? = null): Boolean {
        val columns = settingsManager.columns
        val sX = ceil(spanX.toDouble() - 0.01).toInt()
        val sY = ceil(spanY.toDouble() - 0.01).toInt()
        if (col < 0 || row < 0 || col + sX > columns || row + sY > GRID_ROWS) return false
        val occupied = getOccupiedCells(pageIndex, excludeItem)
        return canPlace(occupied, row, col, sX, sY)
    }

    fun hasAnySpace(spanX: Float, spanY: Float, pageIndex: Int): Boolean {
        val columns = settingsManager.columns
        val occupied = getOccupiedCells(pageIndex)
        for (r in 0..GRID_ROWS - spanY.roundToInt()) {
            for (c in 0..columns - spanX.roundToInt()) {
                if (canPlace(occupied, r, c, spanX.roundToInt(), spanY.roundToInt())) return true
            }
        }
        return false
    }

    companion object {
        const val GRID_COLUMNS = 4
        const val GRID_ROWS = 6
        const val HORIZONTAL_PADDING_DP = 8
    }
}
