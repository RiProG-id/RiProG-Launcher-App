package com.riprog.launcher.ui.views.layout

import com.riprog.launcher.ui.views.home.HomeView
import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.R

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.util.TypedValue
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.sqrt

class MainLayout(private val activity: MainActivity) : FrameLayout(activity) {
    private var isDrawerOpen = false
    var startX = 0f
        private set
    var startY = 0f
        private set
    var lastX = 0f
        private set
    var lastY = 0f
        private set
    private var downTime: Long = 0
    private var isGestureCanceled = false
    private val touchSlop: Int = ViewConfiguration.get(activity).scaledTouchSlop
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var touchedView: View? = null
    private var longPressTriggered = false
    private var isDragging = false

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        if (touchedView != null) {
            activity.freeformInteraction.showTransformOverlay(touchedView!!, startX, startY)
        } else {
            val cellWidth = activity.homeView.getCellWidth()
            val cellHeight = activity.homeView.getCellHeight()
            val horizontalPadding = dpToPx(HomeView.HORIZONTAL_PADDING_DP)
            val col = (startX - horizontalPadding) / if (cellWidth > 0) cellWidth else 1.0f
            val row = startY / if (cellHeight > 0) cellHeight else 1.0f
            activity.showHomeContextMenu(col, row, activity.homeView.currentPage)
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        lastX = ev.x
        lastY = ev.y

        if (activity.freeformInteraction.isTransforming()) {
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

                    if (dy > touchSlop && dy > abs(dx.toDouble())) {
                        if (activity.drawerView.isAtTop() || dy > touchSlop * 4) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        when (ev.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                lastX = ev.x
                lastY = ev.y
                downTime = System.currentTimeMillis()
                isGestureCanceled = false
                longPressTriggered = false
                touchedView = findTouchedHomeItem(startX, startY)
                longPressHandler.removeCallbacks(longPressRunnable)
                if (!activity.freeformInteraction.isTransforming()) {
                    longPressHandler.postDelayed(longPressRunnable, 400)
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - startX
                val dy = ev.y - startY

                if (activity.freeformInteraction.isTransforming()) {
                    return false
                }

                if (dy < -touchSlop && abs(dy.toDouble()) > abs(dx.toDouble())) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    return true
                }
                if (abs(dx.toDouble()) > touchSlop || abs(dy.toDouble()) > touchSlop) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
                return isDragging
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) return true
                val duration = System.currentTimeMillis() - downTime
                if (duration < 80) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    return false
                }
            }
        }
        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        lastX = event.x
        lastY = event.y

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

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                // startX/startY already set in onInterceptTouchEvent
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY

                if (activity.freeformInteraction.isTransforming()) {
                    activity.freeformInteraction.currentTransformOverlay?.dispatchTouchEvent(event)
                    return true
                }

                if (isDragging) {
                    activity.homeView.handleDrag(event.x, event.y)
                    return true
                }

                if (!isGestureCanceled && (abs(dx.toDouble()) > touchSlop || abs(dy.toDouble()) > touchSlop)) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (abs(dy.toDouble()) > abs(dx.toDouble())) {
                        if (dy < -touchSlop * 2) {
                            openDrawer()
                            isGestureCanceled = true
                        }
                    } else {
                        if (dx > touchSlop * 2 && activity.homeView.currentPage > 0) {
                            activity.homeView.scrollToPage(activity.homeView.currentPage - 1)
                            isGestureCanceled = true
                        } else if (dx < -touchSlop * 2 && activity.homeView.currentPage < activity.homeView.getPageCount() - 1) {
                            activity.homeView.scrollToPage(activity.homeView.currentPage + 1)
                            isGestureCanceled = true
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                if (activity.freeformInteraction.isTransforming()) {
                    activity.freeformInteraction.currentTransformOverlay?.dispatchTouchEvent(event)
                    return true
                }
                if (isDragging) {
                    activity.homeView.endDragging()
                    isDragging = false
                    return true
                }
                if (!isGestureCanceled && !longPressTriggered) {
                    val duration = System.currentTimeMillis() - downTime
                    val finalDx = event.x - startX
                    val finalDy = event.y - startY
                    val dist = sqrt((finalDx * finalDx + finalDy * finalDy).toDouble()).toFloat()
                    if (duration >= 80 && duration < 350 && dist < touchSlop) {
                        if (touchedView != null) activity.handleItemClick(touchedView!!)
                        else performClick()
                    }
                }
                return true
            }
        }
        return true
    }

    fun findTouchedHomeItem(x: Float, y: Float, exclude: View? = null): View? {
        val homeView = activity.homeView
        val pagesContainer = homeView.pagesContainer

        // Strategy: First check current page, then check others
        val currentIdx = homeView.currentPage
        val indices = mutableListOf(currentIdx)
        for (i in 0 until homeView.pages.size) {
            if (i != currentIdx) indices.add(i)
        }

        for (pageIdx in indices) {
            if (pageIdx >= homeView.pages.size) continue
            val pageLayout = homeView.pages[pageIdx]

            val pageAbsX = pagesContainer.translationX + pageLayout.left
            val pageAbsY = pagesContainer.translationY + pageLayout.top

            val adjustedX = x - pageAbsX
            val adjustedY = y - pageAbsY

            for (i in pageLayout.childCount - 1 downTo 0) {
                val child = pageLayout.getChildAt(i)
                if (child === exclude) continue

                val item = child.tag as? HomeItem
                val cellWidth = homeView.getCellWidth()
                val cellHeight = homeView.getCellHeight()

                val w = if (child.width > 0) child.width.toFloat() else (item?.spanX?.toFloat()?.times(cellWidth) ?: 0f)
                val h = if (child.height > 0) child.height.toFloat() else (item?.spanY?.toFloat()?.times(cellHeight) ?: 0f)

                if (adjustedX >= child.x && adjustedX <= child.x + w &&
                    adjustedY >= child.y && adjustedY <= child.y + h
                ) {
                    return child
                }
            }
        }
        return null
    }

    fun openDrawer() {
        if (isDrawerOpen) return
        isDrawerOpen = true
        activity.settingsManager.drawerOpenCount = activity.settingsManager.drawerOpenCount + 1
        activity.drawerView.visibility = View.VISIBLE
        activity.drawerView.alpha = 0f
        activity.drawerView.translationY = height / 4f
        activity.drawerView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        activity.homeView.animate().alpha(0f).setDuration(250).start()
        activity.drawerView.onOpen()
    }

    fun startExternalDrag(v: View) {
        isDragging = true
        touchedView = v

        val cellWidth = activity.homeView.getCellWidth()
        val cellHeight = activity.homeView.getCellHeight()

        val item = v.tag as? HomeItem

        var w = 0f
        var h = 0f

        if (v.width > 0) {
            w = v.width.toFloat()
            h = v.height.toFloat()
        } else if (v.layoutParams != null && v.layoutParams.width > 0) {
            w = v.layoutParams.width.toFloat()
            h = v.layoutParams.height.toFloat()
        } else if (item != null && cellWidth > 0 && cellHeight > 0) {
            w = item.spanX * cellWidth
            h = item.spanY * cellHeight
        } else {
            val iconSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size).toFloat()
            w = if (cellWidth > 0) cellWidth else iconSize
            h = if (cellHeight > 0) cellHeight else iconSize * 1.2f
        }

        if (v.layoutParams == null || v.layoutParams.width <= 0) {
            v.layoutParams = LayoutParams(w.toInt(), h.toInt())
        }

        v.pivotX = w / 2f
        v.pivotY = h / 2f

        v.x = lastX - w / 2f
        v.y = lastY - h / 2f

        activity.homeView.startDragging(v, lastX, lastY)
    }

    fun closeDrawer() {
        if (!isDrawerOpen) return
        isDrawerOpen = false
        activity.drawerView.animate()
            .translationY(height / 4f)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                activity.drawerView.visibility = View.GONE
                activity.homeView.visibility = View.VISIBLE
                activity.drawerView.onClose()
                System.gc()
            }
            .start()
        activity.homeView.visibility = View.VISIBLE
        activity.homeView.animate().alpha(1f).setDuration(200).start()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun handleItemClick(v: View) {
        val item = v.tag as HomeItem? ?: return
        if (item.type == HomeItem.Type.APP) {
            val intent = activity.packageManager.getLaunchIntentForPackage(item.packageName!!)
            if (intent != null) activity.startActivity(intent)
        } else if (item.type == HomeItem.Type.WIDGET) {
            activity.showWidgetOptions(item, v)
        } else if (item.type == HomeItem.Type.FOLDER) {
            activity.folderManager.openFolder(item, v, activity.homeItems, activity.allApps)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
