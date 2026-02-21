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
    private var startX = 0f
    private var startY = 0f
    private var downTime: Long = 0
    private var isGestureCanceled = false
    private val touchSlop: Int = ViewConfiguration.get(activity).scaledTouchSlop
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var touchedView: View? = null
    private var longPressTriggered = false
    private var isDragging = false
    private var dragOverlay: LinearLayout? = null
    private var ivRemove: ImageView? = null
    private var ivAppInfo: ImageView? = null
    private var origCol = 0f
    private var origRow = 0f
    private var origPage = 0
    private var isExternalDrag = false

    private var lastDist = 0f
    private var lastAngle = 0f
    private var baseScale = 0f
    private var baseRotation = 0f
    private var baseTiltX = 0f
    private var baseTiltY = 0f
    private var startX3 = 0f
    private var startY3 = 0f

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        if (touchedView != null) {
            if (activity.settingsManager.isFreeformHome) {
                activity.freeformInteraction.showTransformOverlay(touchedView!!)
            } else {
                isDragging = true
                isExternalDrag = false
                val item = touchedView!!.tag as HomeItem?
                if (item != null) {
                    origCol = item.col
                    origRow = item.row
                    origPage = item.page
                }
                if (dragOverlay != null) {
                    val isApp = item != null && item.type == HomeItem.Type.APP
                    ivAppInfo!!.visibility = if (isApp) View.VISIBLE else View.GONE
                    dragOverlay!!.visibility = View.VISIBLE
                }
                activity.homeView.startDragging(touchedView!!, startX, startY)
            }
        } else {
            val cellWidth = width / HomeView.GRID_COLUMNS
            val cellHeight = (height - dpToPx(48)) / HomeView.GRID_ROWS
            val col = startX / if (cellWidth > 0) cellWidth.toFloat() else 1.0f
            val row = (startY - dpToPx(48)) / if (cellHeight > 0) cellHeight.toFloat() else 1.0f
            activity.showHomeContextMenu(col, row, activity.homeView.currentPage)
        }
    }

    init {
        setupDragOverlay()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun setupDragOverlay() {
        dragOverlay = LinearLayout(context)
        dragOverlay!!.orientation = LinearLayout.HORIZONTAL
        dragOverlay!!.setBackgroundResource(R.drawable.glass_bg)
        dragOverlay!!.gravity = Gravity.CENTER
        dragOverlay!!.visibility = View.GONE
        dragOverlay!!.elevation = dpToPx(8).toFloat()

        ivRemove = ImageView(context)
        ivRemove!!.setImageResource(R.drawable.ic_remove)
        ivRemove!!.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        ivRemove!!.contentDescription = context.getString(R.string.drag_remove)
        dragOverlay!!.addView(ivRemove)

        ivAppInfo = ImageView(context)
        ivAppInfo!!.setImageResource(R.drawable.ic_info)
        ivAppInfo!!.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        ivAppInfo!!.contentDescription = context.getString(R.string.drag_app_info)
        dragOverlay!!.addView(ivAppInfo)

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = dpToPx(48)
        addView(dragOverlay, lp)
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun angle(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val deltaX = (event.getX(0) - event.getX(1)).toDouble()
        val deltaY = (event.getY(0) - event.getY(1)).toDouble()
        val radians = Math.atan2(deltaY, deltaX)
        return Math.toDegrees(radians).toFloat()
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
                downTime = System.currentTimeMillis()
                isGestureCanceled = false
                longPressTriggered = false
                isDragging = false
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
                    if (!longPressTriggered) {
                        return true
                    }
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
            MotionEvent.ACTION_DOWN -> return true

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isDragging && activity.settingsManager.isFreeformHome) {
                    if (event.pointerCount == 2) {
                        lastDist = spacing(event)
                        lastAngle = angle(event)
                        baseScale = touchedView!!.scaleX
                        baseRotation = touchedView!!.rotation
                    } else if (event.pointerCount == 3) {
                        startX3 = event.getX(2)
                        startY3 = event.getY(2)
                        baseTiltX = touchedView!!.rotationX
                        baseTiltY = touchedView!!.rotationY
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY

                if (activity.freeformInteraction.isTransforming()) {
                    return true
                }

                if (isDragging) {
                    if (activity.settingsManager.isFreeformHome && event.pointerCount > 1) {
                        if (event.pointerCount == 2) {
                            val newDist = spacing(event)
                            if (newDist > 10f) {
                                val scaleFactor = newDist / lastDist
                                touchedView!!.scaleX = baseScale * scaleFactor
                                touchedView!!.scaleY = baseScale * scaleFactor
                            }
                            val newAngle = angle(event)
                            touchedView!!.rotation = baseRotation + (newAngle - lastAngle)
                        } else if (event.pointerCount == 3) {
                            val mdx = event.getX(2) - startX3
                            val mdy = event.getY(2) - startY3
                            touchedView!!.rotationX = baseTiltX + mdy / 5f
                            touchedView!!.rotationY = baseTiltY - mdx / 5f
                        }
                    } else {
                        activity.homeView.handleDrag(event.x, event.y)
                        updateDragHighlight(event.x, event.y)
                    }
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
                if (isDragging) {
                    if (dragOverlay != null) {
                        val overlayHeight = dragOverlay!!.height
                        val overlayWidth = dragOverlay!!.width
                        val left = (width - overlayWidth) / 2f
                        dragOverlay!!.visibility = View.GONE
                        ivRemove!!.setBackgroundColor(Color.TRANSPARENT)
                        ivAppInfo!!.setBackgroundColor(Color.TRANSPARENT)

                        if (event.y < dragOverlay!!.bottom + touchSlop * 2 &&
                            event.x >= left && event.x <= left + overlayWidth
                        ) {
                            val item = touchedView!!.tag as HomeItem?
                            if (item != null) {
                                val isApp = ivAppInfo!!.visibility == View.VISIBLE
                                if (!isApp) {
                                    activity.removeHomeItem(item, touchedView)
                                } else {
                                    val x = event.x
                                    if (x < left + overlayWidth / 2f) {
                                        activity.removeHomeItem(item, touchedView)
                                    } else {
                                        activity.showAppInfo(item)
                                        revertPosition(item, touchedView!!)
                                    }
                                }
                            }
                            activity.homeView.cancelDragging()
                        } else {
                            activity.homeView.endDragging()
                        }
                    } else {
                        activity.homeView.endDragging()
                    }
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
        val page = activity.homeView.currentPage
        val pagesContainer = activity.homeView.getChildAt(0) as ViewGroup?
        if (pagesContainer != null && page < pagesContainer.childCount) {
            val pageLayout = pagesContainer.getChildAt(page) as ViewGroup

            val pageAbsX = pagesContainer.translationX + pageLayout.left
            val pageAbsY = pagesContainer.translationY + pageLayout.top

            val adjustedX = x - pageAbsX
            val adjustedY = y - pageAbsY

            for (i in pageLayout.childCount - 1 downTo 0) {
                val child = pageLayout.getChildAt(i)
                if (child === exclude) continue
                if (adjustedX >= child.x && adjustedX <= child.x + child.width &&
                    adjustedY >= child.y && adjustedY <= child.y + child.height
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
        isExternalDrag = true
        if (dragOverlay != null) {
            val item = v.tag as HomeItem?
            val isApp = item != null && item.type == HomeItem.Type.APP
            ivAppInfo!!.visibility = if (isApp) View.VISIBLE else View.GONE
            dragOverlay!!.visibility = View.VISIBLE
        }
        touchedView = v

        val iconSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        v.x = startX - iconSize / 2f
        v.y = startY - iconSize / 2f - dpToPx(48)

        activity.homeView.startDragging(v, startX, startY)
    }

    private fun updateDragHighlight(x: Float, y: Float) {
        if (dragOverlay == null || dragOverlay!!.visibility != View.VISIBLE) return

        val overlayWidth = dragOverlay!!.width
        val left = (width - overlayWidth) / 2f
        val isApp = ivAppInfo!!.visibility == View.VISIBLE

        ivRemove!!.setBackgroundColor(Color.TRANSPARENT)
        ivAppInfo!!.setBackgroundColor(Color.TRANSPARENT)

        if (y < dragOverlay!!.bottom + touchSlop * 2 && x >= left && x <= left + overlayWidth) {
            if (!isApp) {
                ivRemove!!.setBackgroundColor(0x40FFFFFF)
            } else {
                if (x < left + overlayWidth / 2f) {
                    ivRemove!!.setBackgroundColor(0x40FFFFFF)
                } else {
                    ivAppInfo!!.setBackgroundColor(0x40FFFFFF)
                }
            }
        }
    }

    private fun revertPosition(item: HomeItem, v: View) {
        if (isExternalDrag) {
            activity.removeHomeItem(item, v)
        } else {
            item.col = origCol
            item.row = origRow
            item.page = origPage
            activity.homeView.addItemView(item, v)
            v.rotation = item.rotation
            v.scaleX = item.scale
            v.scaleY = item.scale
            v.rotationX = item.tiltX
            v.rotationY = item.tiltY
            activity.homeView.updateViewPosition(item, v)
            activity.saveHomeState()
        }
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
