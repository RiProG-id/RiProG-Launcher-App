package com.riprog.launcher.ui

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.riprog.launcher.R
import com.riprog.launcher.manager.GridManager
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.utils.SettingsManager
import com.riprog.launcher.utils.ThemeUtils

class MainLayout(context: Context, private val callback: Callback) : FrameLayout(context) {
    interface Callback {
        fun isTransforming(): Boolean
        fun isFolderOpen(): Boolean
        fun getHomeView(): HomeView?
        fun getDrawerView(): DrawerView?
        fun getSettingsManager(): SettingsManager
        fun showHomeContextMenu(col: Float, row: Float, page: Int)
        fun showTransformOverlay(v: View)
        fun startTransformDirectMove(v: View, x: Float, y: Float)
        fun handleItemClick(v: View)
        fun removeHomeItem(item: HomeItem, v: View)
        fun showAppInfo(item: HomeItem)
        fun saveHomeState()
        fun scrollToPage(page: Int)
        fun getCurrentPage(): Int
        fun openDrawer()
        fun closeDrawer()
        fun closeDrawerInstantly()
        fun getTransformOverlay(): View?
    }

    private var dimView: View? = null
    var isDrawerOpen = false
        private set
    private var startX = 0f
    private var startY = 0f
    private var downTime: Long = 0
    private var isGestureCanceled = false
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var touchedView: View? = null
    private var longPressTriggered = false
    private val dragController: DragController = DragController(this, callback)

    private val longPressRunnable = Runnable {
        if (dragController.isDragging() || callback.isTransforming() || isDrawerOpen || callback.isFolderOpen()) return@Runnable
        longPressTriggered = true
        if (touchedView != null) {
            val tag = touchedView!!.tag
            if (tag is HomeItem) {
                if (callback.getSettingsManager().isFreeformHome) {
                    callback.showTransformOverlay(touchedView!!)
                    callback.startTransformDirectMove(touchedView!!, startX, startY)
                    return@Runnable
                }
                dragController.startDrag(touchedView!!, startX, startY)
            }
        } else {
            val hv = callback.getHomeView()
            val grid = hv?.gridManager ?: GridManager(callback.getSettingsManager().columns)
            val cellWidth = grid.getCellWidth(width)
            val cellHeight = grid.getCellHeight(height)
            val col = startX / if (cellWidth > 0) cellWidth.toFloat() else 1.0f
            val row = startY / if (cellHeight > 0) cellHeight.toFloat() else 1.0f
            callback.showHomeContextMenu(col, row, callback.getCurrentPage())
        }
    }

    init {
        setupDimView()
    }

    private fun setupDimView() {
        dimView = View(context)
        dimView!!.setBackgroundColor(Color.BLACK)
        dimView!!.alpha = 0.3f
        dimView!!.visibility = GONE
        addView(dimView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun updateDimVisibility() {
        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isNight && callback.getSettingsManager().isDarkenWallpaper) {
            dimView!!.visibility = VISIBLE
        } else {
            dimView!!.visibility = GONE
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun resetDragState() {
        longPressHandler.removeCallbacks(longPressRunnable)
        dragController.resetDragState()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            startX = ev.x
            startY = ev.y
            downTime = System.currentTimeMillis()
        }
        if (callback.isTransforming()) return false
        if (callback.isFolderOpen()) {
            longPressHandler.removeCallbacks(longPressRunnable)
            return false
        }
        if (isDrawerOpen) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    startY = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.y - startY
                    val dx = ev.x - startX
                    if (dy > touchSlop && dy > Math.abs(dx)) {
                        if (callback.getDrawerView()?.isAtTop() == true || dy > touchSlop * 4) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                downTime = System.currentTimeMillis()
                isGestureCanceled = false
                longPressTriggered = false
                resetDragState()
                touchedView = findTouchedHomeItem(startX, startY)
                longPressHandler.postDelayed(longPressRunnable, 400)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - startX
                val dy = ev.y - startY
                if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    return true
                }
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (!longPressTriggered) {
                        return true
                    }
                }
                return dragController.isDragging()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragController.isDragging()) return true
                val duration = System.currentTimeMillis() - downTime
                if (duration < 80) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    return false
                }
            }
        }
        return dragController.isDragging()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (callback.isTransforming()) {
            callback.getTransformOverlay()?.dispatchTouchEvent(event)
            return true
        }

        if (isDrawerOpen) {
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

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (dragController.isDragging()) {
                    dragController.handleDrag(event)
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
                        if (dx > touchSlop * 2) {
                            callback.scrollToPage(callback.getCurrentPage() - 1)
                            isGestureCanceled = true
                        } else if (dx < -touchSlop * 2) {
                            callback.scrollToPage(callback.getCurrentPage() + 1)
                            isGestureCanceled = true
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragController.isDragging()) {
                    if (dragController.onActionUp(event)) {
                        return true
                    }
                    return true
                }
                resetDragState()
                if (!isGestureCanceled && !longPressTriggered) {
                    val duration = System.currentTimeMillis() - downTime
                    val finalDx = event.x - startX
                    val finalDy = event.y - startY
                    val dist = Math.sqrt((finalDx * finalDx + finalDy * finalDy).toDouble()).toFloat()
                    if (duration in 50..299 && dist < touchSlop) {
                        touchedView?.let { callback.handleItemClick(it) } ?: performClick()
                    }
                }
                return true
            }
        }
        return true
    }

    private fun findTouchedHomeItem(x: Float, y: Float): View? {
        val homeView = callback.getHomeView() ?: return null
        val page = homeView.getCurrentPage()
        val pagesContainer = homeView.pagesContainer
        if (page < pagesContainer.childCount) {
            val pageLayout = pagesContainer.getChildAt(page) as? ViewGroup ?: return null
            val pagePos = IntArray(2)
            pageLayout.getLocationOnScreen(pagePos)
            val rootPos = IntArray(2)
            getLocationOnScreen(rootPos)
            val adjX = x - (pagePos[0] - rootPos[0])
            val adjY = y - (pagePos[1] - rootPos[1])
            for (i in pageLayout.childCount - 1 downTo 0) {
                val child = pageLayout.getChildAt(i)
                if (homeView.getVisualRect(child).contains(adjX, adjY)) return child
            }
        }
        return null
    }

    fun openDrawer() {
        if (isDrawerOpen) return
        isDrawerOpen = true
        callback.getSettingsManager().incrementDrawerOpenCount()
        val drawerView = callback.getDrawerView() ?: return
        drawerView.visibility = VISIBLE
        drawerView.alpha = 0f
        drawerView.translationY = height / 4f
        drawerView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        val targetAlpha = if (callback.getSettingsManager().isLiquidGlass) 0.4f else 0f
        callback.getHomeView()?.animate()?.alpha(targetAlpha)?.setDuration(250)?.start()
        drawerView.onOpen()
    }

    fun closeDrawer() {
        if (!isDrawerOpen) return
        isDrawerOpen = false
        val drawerView = callback.getDrawerView() ?: return
        drawerView.animate()
            .translationY(height / 4f)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                drawerView.visibility = GONE
                callback.getHomeView()?.visibility = VISIBLE
                drawerView.onClose()
                System.gc()
            }
            .start()
        callback.getHomeView()?.visibility = VISIBLE
        callback.getHomeView()?.animate()?.alpha(1f)?.setDuration(200)?.start()
    }

    fun closeDrawerInstantly() {
        if (!isDrawerOpen) return
        isDrawerOpen = false
        val drawerView = callback.getDrawerView() ?: return
        drawerView.animate().cancel()
        drawerView.visibility = GONE
        drawerView.alpha = 0f
        drawerView.translationY = height / 4f
        drawerView.onClose()
        callback.getHomeView()?.visibility = VISIBLE
        callback.getHomeView()?.alpha = 1f
        System.gc()
    }

    fun startExternalDrag(v: View) {
        dragController.startDrag(v, startX, startY, true)
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
