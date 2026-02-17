package com.riprog.launcher.ui.drag

import com.riprog.launcher.ui.home.MainLayout

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.riprog.launcher.R
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.ui.common.ThemeUtils

class DragController(
    private val layout: MainLayout,
    private val callback: MainLayout.Callback
) {
    private val touchSlop: Int = ViewConfiguration.get(layout.context).scaledTouchSlop
    private var isDragging = false
    private var dragOverlay: LinearLayout? = null
    private var ivRemove: ImageView? = null
    private var ivAppInfo: ImageView? = null
    private var touchedView: View? = null
    private var isExternalDrag = false

    private var origCol = 0f
    private var origRow = 0f
    private var origPage = 0

    init {
        setupDragOverlay()
    }

    private fun setupDragOverlay() {
        dragOverlay = LinearLayout(layout.context)
        dragOverlay!!.orientation = LinearLayout.HORIZONTAL
        dragOverlay!!.background = ThemeUtils.getGlassDrawable(layout.context, callback.getSettingsManager(), 12f)
        dragOverlay!!.gravity = Gravity.CENTER
        dragOverlay!!.visibility = View.GONE
        dragOverlay!!.elevation = dpToPx(8).toFloat()

        val adaptiveColor = ThemeUtils.getAdaptiveColor(layout.context, callback.getSettingsManager(), true)

        ivRemove = ImageView(layout.context)
        ivRemove!!.setImageResource(R.drawable.ic_remove)
        ivRemove!!.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        ivRemove!!.setColorFilter(adaptiveColor)
        ivRemove!!.contentDescription = layout.context.getString(R.string.drag_remove)
        dragOverlay!!.addView(ivRemove)

        ivAppInfo = ImageView(layout.context)
        ivAppInfo!!.setImageResource(R.drawable.ic_info)
        ivAppInfo!!.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        ivAppInfo!!.setColorFilter(adaptiveColor)
        ivAppInfo!!.contentDescription = layout.context.getString(R.string.drag_app_info)
        dragOverlay!!.addView(ivAppInfo)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = dpToPx(48)
        layout.addView(dragOverlay, lp)
    }

    fun startDrag(v: View, x: Float, y: Float, isExternal: Boolean = false) {
        isDragging = true
        isExternalDrag = isExternal
        touchedView = v
        val item = v.tag as? HomeItem
        if (item != null && !isExternal) {
            origCol = item.col
            origRow = item.row
            origPage = item.page
        }

        dragOverlay?.let {
            val isApp = item?.type == HomeItem.Type.APP
            ivAppInfo?.visibility = if (isApp) View.VISIBLE else View.GONE
            it.visibility = View.VISIBLE
            it.bringToFront()
        }

        if (isExternal) {
            val lp = v.layoutParams
            if (lp != null && lp.width > 0 && lp.height > 0) {
                v.x = x - lp.width / 2f
                v.y = y - lp.height / 2f
            } else {
                val iconSize = layout.resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                v.x = x - iconSize
                v.y = y - iconSize
            }
        }
        callback.getHomeView()?.startDragging(v, x, y)
    }

    fun handleDrag(event: MotionEvent) {
        if (!isDragging) return
        callback.getHomeView()?.handleDrag(event.x, event.y)
        updateDragHighlight(event.rawX, event.rawY)
    }

    fun onActionUp(event: MotionEvent): Boolean {
        if (!isDragging) return false

        dragOverlay?.let {
            val outPos = IntArray(2)
            it.getLocationOnScreen(outPos)
            val rect = android.graphics.Rect(outPos[0], outPos[1], outPos[0] + it.width, outPos[1] + it.height)
            val hitRect = android.graphics.Rect(rect)
            hitRect.inset(-touchSlop * 4, -touchSlop * 4)

            if (touchedView != null && hitRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                val tag = touchedView!!.tag as? HomeItem
                if (tag != null) {
                    val isApp = ivAppInfo?.visibility == View.VISIBLE
                    if (!isApp) {
                        callback.removeHomeItem(tag, touchedView!!)
                    } else {
                        if (event.rawX < rect.left + rect.width() / 2f) {
                            callback.removeHomeItem(tag, touchedView!!)
                        } else {
                            callback.showAppInfo(tag)
                            revertPosition(tag, touchedView!!)
                        }
                    }
                }
                resetDragState()
                return true
            }
        }

        callback.getHomeView()?.endDragging()
        resetDragState()
        return true
    }

    fun resetDragState() {
        isDragging = false
        dragOverlay?.let {
            it.visibility = View.GONE
            ivRemove?.setBackgroundColor(Color.TRANSPARENT)
            ivAppInfo?.setBackgroundColor(Color.TRANSPARENT)
        }
        callback.getHomeView()?.cancelDragging()
    }

    private fun updateDragHighlight(rawX: Float, rawY: Float) {
        dragOverlay?.let {
            if (it.visibility != View.VISIBLE) return@let
            it.bringToFront()

            val outPos = IntArray(2)
            it.getLocationOnScreen(outPos)
            val rect = android.graphics.Rect(outPos[0], outPos[1], outPos[0] + it.width, outPos[1] + it.height)

            val isApp = ivAppInfo?.visibility == View.VISIBLE
            ivRemove?.setBackgroundColor(Color.TRANSPARENT)
            ivAppInfo?.setBackgroundColor(Color.TRANSPARENT)

            val hitRect = android.graphics.Rect(rect)
            hitRect.inset(-touchSlop * 4, -touchSlop * 4)

            if (hitRect.contains(rawX.toInt(), rawY.toInt())) {
                if (!isApp) {
                    ivRemove?.setBackgroundColor(0x40FFFFFF)
                } else {
                    if (rawX < rect.left + rect.width() / 2f) ivRemove?.setBackgroundColor(0x40FFFFFF)
                    else ivAppInfo?.setBackgroundColor(0x40FFFFFF)
                }
            }
        }
    }

    private fun revertPosition(item: HomeItem, v: View) {
        if (isExternalDrag) {
            callback.removeHomeItem(item, v)
        } else {
            item.col = origCol
            item.row = origRow
            item.page = origPage
            callback.getHomeView()?.addItemView(item, v)
            v.rotation = item.rotation
            v.scaleX = item.scaleX
            v.scaleY = item.scaleY
            v.rotationX = item.tiltX
            v.rotationY = item.tiltY
            callback.getHomeView()?.updateViewPosition(item, v)
            callback.saveHomeState()
        }
    }

    fun isDragging(): Boolean = isDragging

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), layout.resources.displayMetrics
        ).toInt()
    }
}
