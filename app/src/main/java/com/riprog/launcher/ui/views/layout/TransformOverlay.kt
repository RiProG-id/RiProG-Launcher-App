package com.riprog.launcher.ui.views.layout

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.logic.utils.WidgetSizingUtils
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.ui.views.home.HomeView
import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.R

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.*

@SuppressLint("ViewConstructor")
class TransformOverlay(context: Context, private val targetView: View, private val settingsManager: SettingsManager, private val onSaveListener: OnSaveListener?) : FrameLayout(context) {

    private val item: HomeItem = targetView.tag as HomeItem
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleSize: Float = dpToPx(12f).toFloat()
    private val rotationHandleDist: Float = dpToPx(50f).toFloat()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var gestureInitialScaleX = 0f
    private var gestureInitialScaleY = 0f
    private var gestureInitialX = 0f
    private var gestureInitialY = 0f
    private var gestureInitialWidth = 0
    private var gestureInitialHeight = 0
    private var gestureInitialBounds: RectF? = null
    private var gestureInitialSpanX = 1
    private var gestureInitialSpanY = 1
    private var gestureInitialCol = 0f
    private var gestureInitialRow = 0f
    private var currentDrx = 0f
    private var currentDry = 0f
    private var hasPassedThreshold = false
    private var activeHandle = -1
    private var canResizeHorizontal = true
    private var canResizeVertical = true

    private val initialRotation: Float = targetView.rotation
    private val initialScaleX: Float = targetView.scaleX
    private val initialScaleY: Float = targetView.scaleY
    private val initialX: Float = targetView.x
    private val initialY: Float = targetView.y

    interface OnSaveListener {
        fun onMove(x: Float, y: Float)
        fun onMoveStart(x: Float, y: Float)
        fun onSave()
        fun onCancel()
        fun onRemove()
        fun onAppInfo()
        fun onCollision(otherView: View)
        fun findItemAt(x: Float, y: Float, exclude: View): View?
        fun onSnapToGrid(v: View)
    }

    init {
        if (targetView is AppWidgetHostView) {
            val info = targetView.appWidgetInfo
            if (info != null) {
                canResizeHorizontal = (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
                canResizeVertical = (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
            }
        }
        setWillNotDraw(false)
        setupButtons()
    }

    fun startImmediateDrag(x: Float, y: Float) {
        activeHandle = ACTION_MOVE
        initialTouchX = x
        initialTouchY = y
        lastTouchX = x
        lastTouchY = y
        gestureInitialScaleX = targetView.scaleX
        gestureInitialScaleY = targetView.scaleY
        gestureInitialX = targetView.x
        gestureInitialY = targetView.y
        gestureInitialWidth = targetView.width
        gestureInitialHeight = targetView.height
        gestureInitialBounds = contentBounds
        gestureInitialSpanX = item.spanX
        gestureInitialSpanY = item.spanY
        gestureInitialCol = item.col
        gestureInitialRow = item.row
        currentDrx = 0f
        currentDry = 0f
        hasPassedThreshold = true // Already long pressed
        onSaveListener?.onMoveStart(x, y)
    }

    private fun setupButtons() {
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)

        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER
        container.setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
        container.background = ThemeUtils.getGlassDrawable(context, settingsManager, 12f)

        addButton(container, R.string.action_remove, adaptiveColor) { onSaveListener?.onRemove() }
        addButton(container, R.string.action_reset, adaptiveColor) { reset() }
        addButton(container, R.string.action_save, adaptiveColor) { onSaveListener?.onSave() }

        if (item.type == HomeItem.Type.APP) {
            addButton(container, R.string.action_app_info, adaptiveColor) { onSaveListener?.onAppInfo() }
        }

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.bottomMargin = dpToPx(48f)
        lp.leftMargin = dpToPx(24f)
        lp.rightMargin = dpToPx(24f)
        addView(container, lp)
    }

    private fun addButton(parent: LinearLayout, textRes: Int, color: Int, listener: OnClickListener) {
        val btn = TextView(context)
        btn.setText(textRes)
        btn.setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
        btn.setTextColor(color)
        btn.textSize = 11f
        btn.setTypeface(null, Typeface.BOLD)
        btn.gravity = Gravity.CENTER
        btn.setOnClickListener(listener)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        parent.addView(btn, lp)
    }

    private fun reset() {
        targetView.rotation = 0f
        targetView.scaleX = 1.0f
        targetView.scaleY = 1.0f

        // Restore widget to provider-based calculated span
        if (item.type == HomeItem.Type.WIDGET && targetView is AppWidgetHostView) {
            val info = targetView.appWidgetInfo
            if (info != null && context is MainActivity) {
                val activity = context as MainActivity
                val spans = WidgetSizingUtils.calculateWidgetSpan(activity, activity.homeView, info)
                item.spanX = spans.first
                item.spanY = spans.second
                activity.homeView.updateViewPosition(item, targetView)
            }
        } else if (item.type == HomeItem.Type.APP || item.type == HomeItem.Type.FOLDER) {
            item.spanX = 1
            item.spanY = 1
            if (context is MainActivity) {
                (context as MainActivity).homeView.updateViewPosition(item, targetView)
            }
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val isFreeform = settingsManager.isFreeformHome
        val isWidget = item.type == HomeItem.Type.WIDGET
        val sx = targetView.scaleX
        val sy = targetView.scaleY
        val r = if (isFreeform) targetView.rotation else 0f

        val isEdgeResizing = activeHandle == HANDLE_TOP || activeHandle == HANDLE_BOTTOM || activeHandle == HANDLE_LEFT || activeHandle == HANDLE_RIGHT
        val cx = if (isEdgeResizing) gestureInitialX + gestureInitialWidth / 2f else targetView.x + targetView.pivotX
        val cy = if (isEdgeResizing) gestureInitialY + gestureInitialHeight / 2f else targetView.y + targetView.pivotY

        val bounds = if (activeHandle != -1 && gestureInitialBounds != null) gestureInitialBounds!! else contentBounds
        var left = (bounds.left - (if (isEdgeResizing) gestureInitialWidth / 2f else targetView.pivotX)) * sx
        var top = (bounds.top - (if (isEdgeResizing) gestureInitialHeight / 2f else targetView.pivotY)) * sy
        var right = (bounds.right - (if (isEdgeResizing) gestureInitialWidth / 2f else targetView.pivotX)) * sx
        var bottom = (bounds.bottom - (if (isEdgeResizing) gestureInitialHeight / 2f else targetView.pivotY)) * sy

        // Adjust guideline based on continuous displacement
        if (isEdgeResizing) {
            when (activeHandle) {
                HANDLE_RIGHT -> right += currentDrx
                HANDLE_LEFT -> left += currentDrx
                HANDLE_BOTTOM -> bottom += currentDry
                HANDLE_TOP -> top += currentDry
            }
        }

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(r)

        val foregroundColor = ThemeUtils.getAdaptiveColor(context, settingsManager, false)
        paint.color = foregroundColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpToPx(1f).toFloat()
        paint.alpha = 60

        // Only show directional guide lines (rectangle) for widgets in freeform mode
        if (isFreeform && isWidget) {
            canvas.drawRect(left, top, right, bottom, paint)
        }

        val hs = handleSize / 2f

        if (isFreeform) {
            drawHandle(canvas, left, top, hs, true, foregroundColor)
            drawHandle(canvas, right, top, hs, true, foregroundColor)
            drawHandle(canvas, right, bottom, hs, true, foregroundColor)
            drawHandle(canvas, left, bottom, hs, true, foregroundColor)
        }

        // Only show directional handles for widgets
        if (isWidget) {
            drawHandle(canvas, (left + right) / 2f, top, hs, false, foregroundColor)
            drawHandle(canvas, right, (top + bottom) / 2f, hs, false, foregroundColor)
            drawHandle(canvas, (left + right) / 2f, bottom, hs, false, foregroundColor)
            drawHandle(canvas, left, (top + bottom) / 2f, hs, false, foregroundColor)
        }

        if (isFreeform) {
            paint.color = foregroundColor
            paint.alpha = 100
            canvas.drawLine((left + right) / 2f, top, (left + right) / 2f, top - rotationHandleDist, paint)
            drawHandle(canvas, (left + right) / 2f, top - rotationHandleDist, hs * 1.1f, true, foregroundColor)
        }

        canvas.restore()
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float, isPrimary: Boolean, foregroundColor: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = if (isPrimary) 200 else 120
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.color = foregroundColor
        paint.strokeWidth = dpToPx(1f).toFloat()
        paint.alpha = if (isPrimary) 200 else 150
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private val contentBounds: RectF
        get() {
            if (targetView !is ViewGroup) {
                return RectF(0f, 0f, targetView.width.toFloat(), targetView.height.toFloat())
            }
            val vg = targetView
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            var hasVisibleChildren = false

            for (i in 0 until vg.childCount) {
                val child = vg.getChildAt(i)
                if (child.visibility == View.VISIBLE) {
                    minX = min(minX, child.x)
                    minY = min(minY, child.y)
                    maxX = max(maxX, child.x + child.width)
                    maxY = max(maxY, child.y + child.height)
                    hasVisibleChildren = true
                }
            }

            if (!hasVisibleChildren) {
                return RectF(0f, 0f, targetView.width.toFloat(), targetView.height.toFloat())
            }
            return RectF(minX, minY, maxX, maxY)
        }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                activeHandle = findHandle(x, y)
                if (activeHandle != ACTION_OUTSIDE) {
                    if (activeHandle == ACTION_MOVE && onSaveListener != null) {
                        onSaveListener.onMoveStart(x, y)
                    }
                    initialTouchX = x
                    initialTouchY = y
                    lastTouchX = x
                    lastTouchY = y
                    gestureInitialScaleX = targetView.scaleX
                    gestureInitialScaleY = targetView.scaleY
                    gestureInitialX = targetView.x
                    gestureInitialY = targetView.y
                    gestureInitialWidth = targetView.width
                    gestureInitialHeight = targetView.height
                    gestureInitialBounds = contentBounds
                    gestureInitialSpanX = item.spanX
                    gestureInitialSpanY = item.spanY
                    gestureInitialCol = item.col
                    gestureInitialRow = item.row
                    currentDrx = 0f
                    currentDry = 0f
                    hasPassedThreshold = false
                    return true
                } else {
                    if (onSaveListener != null) {
                        val other = onSaveListener.findItemAt(x, y, targetView)
                        if (other != null) {
                            onSaveListener.onCollision(other)
                            return true
                        }
                        onSaveListener.onSave()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != -1) {
                    if (!hasPassedThreshold) {
                        val threshold = dpToPx(MOVE_THRESHOLD_DP).toFloat()
                        if (dist(x, y, initialTouchX, initialTouchY) > threshold) {
                            hasPassedThreshold = true
                        }
                    }
                    if (hasPassedThreshold) {
                        handleInteraction(x, y)
                    }
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle == ACTION_MOVE && hasPassedThreshold) {
                    val midX = targetView.x + targetView.width / 2f
                    val midY = targetView.y + targetView.height / 2f

                    onSaveListener?.onSnapToGrid(targetView)

                    val other = onSaveListener?.findItemAt(midX, midY, targetView)
                    if (other != null) {
                        onSaveListener?.onCollision(other)
                    }
                }
                activeHandle = -1
                hasPassedThreshold = false
                return true
            }
        }
        return true
    }

    private fun findHandle(tx: Float, ty: Float): Int {
        val isFreeform = settingsManager.isFreeformHome
        val isWidget = item.type == HomeItem.Type.WIDGET
        val sx = targetView.scaleX
        val sy = targetView.scaleY
        val cx = targetView.x + targetView.pivotX
        val cy = targetView.y + targetView.pivotY

        val angle = if (isFreeform) Math.toRadians((-targetView.rotation).toDouble()) else 0.0
        val rx = (cos(angle) * (tx - cx) - sin(angle) * (ty - cy)).toFloat()
        val ry = (sin(angle) * (tx - cx) + cos(angle) * (ty - cy)).toFloat()

        val bounds = contentBounds
        val left = (bounds.left - targetView.pivotX) * sx
        val top = (bounds.top - targetView.pivotY) * sy
        val right = (bounds.right - targetView.pivotX) * sx
        val bottom = (bounds.bottom - targetView.pivotY) * sy

        val hs = dpToPx(24f).toFloat()

        if (isFreeform && dist(rx, ry, (left + right) / 2f, top - rotationHandleDist) < hs) return HANDLE_ROTATE

        if (isFreeform) {
            if (dist(rx, ry, left, top) < hs) return HANDLE_TOP_LEFT
            if (dist(rx, ry, right, top) < hs) return HANDLE_TOP_RIGHT
            if (dist(rx, ry, right, bottom) < hs) return HANDLE_BOTTOM_RIGHT
            if (dist(rx, ry, left, bottom) < hs) return HANDLE_BOTTOM_LEFT
        }

        if (isWidget) {
            if (dist(rx, ry, (left + right) / 2f, top) < hs) return HANDLE_TOP
            if (dist(rx, ry, right, (top + bottom) / 2f) < hs) return HANDLE_RIGHT
            if (dist(rx, ry, (left + right) / 2f, bottom) < hs) return HANDLE_BOTTOM
            if (dist(rx, ry, left, (top + bottom) / 2f) < hs) return HANDLE_LEFT
        }

        return if (rx >= left && rx <= right && ry >= top && ry <= bottom) ACTION_MOVE else ACTION_OUTSIDE
    }

    private fun handleInteraction(tx: Float, ty: Float) {
        val isFreeform = settingsManager.isFreeformHome
        val sx = gestureInitialScaleX
        val sy = gestureInitialScaleY
        val cx = gestureInitialX + targetView.pivotX
        val cy = gestureInitialY + targetView.pivotY

        if (activeHandle == ACTION_MOVE) {
            val newX = gestureInitialX + (tx - initialTouchX)
            val newY = gestureInitialY + (ty - initialTouchY)

            // Smooth movement during drag for "light" feel
            targetView.x = newX
            targetView.y = newY
            onSaveListener?.onMove(tx, ty)
        } else if (activeHandle == HANDLE_ROTATE) {
            val angle = Math.toDegrees(atan2((ty - cy).toDouble(), (tx - cx).toDouble())) + 90
            var targetR = angle.toFloat()
            val currentR = targetView.rotation
            while (targetR - currentR > 180) targetR -= 360f
            while (targetR - currentR < -180) targetR += 360f
            targetView.rotation = currentR + (targetR - currentR) * SMOOTHING_FACTOR
        } else if (activeHandle == HANDLE_TOP || activeHandle == HANDLE_BOTTOM || activeHandle == HANDLE_LEFT || activeHandle == HANDLE_RIGHT) {
            handleEdgeResize(tx, ty)
        } else {
            // Corner-based scaling (freeform only)
            if (!isFreeform) return

            val initialDist = dist(initialTouchX, initialTouchY, cx, cy)
            val currDist = dist(tx, ty, cx, cy)
            if (initialDist > 0) {
                val factor = currDist / initialDist
                targetView.scaleX = sx * factor
                targetView.scaleY = sy * factor
            }
        }
    }

    private fun handleEdgeResize(tx: Float, ty: Float) {
        val activity = context as? MainActivity ?: return
        val cellWidth = activity.homeView.getCellWidth()
        val cellHeight = activity.homeView.getCellHeight()
        if (cellWidth <= 0 || cellHeight <= 0) return

        // Use local coordinates for better accuracy if rotated
        val rotAngle = Math.toRadians((-targetView.rotation).toDouble()).toFloat()
        val cx = gestureInitialX + targetView.pivotX
        val cy = gestureInitialY + targetView.pivotY
        val rx = (cos(rotAngle.toDouble()) * (tx - cx) - sin(rotAngle.toDouble()) * (ty - cy)).toFloat()
        val ry = (sin(rotAngle.toDouble()) * (tx - cx) + cos(rotAngle.toDouble()) * (ty - cy)).toFloat()
        val irx = (cos(rotAngle.toDouble()) * (initialTouchX - cx) - sin(rotAngle.toDouble()) * (initialTouchY - cy)).toFloat()
        val iry = (sin(rotAngle.toDouble()) * (initialTouchX - cx) + cos(rotAngle.toDouble()) * (initialTouchY - cy)).toFloat()

        currentDrx = rx - irx
        currentDry = ry - iry

        var newSpanX = item.spanX
        var newSpanY = item.spanY
        var newCol = item.col.roundToInt()
        var newRow = item.row.roundToInt()

        val info = if (targetView is AppWidgetHostView) targetView.appWidgetInfo else null
        val density = resources.displayMetrics.density

        val minSpanX = if (info != null) WidgetSizingUtils.getMinSpanX(info, cellWidth, density) else 1
        val minSpanY = if (info != null) WidgetSizingUtils.getMinSpanY(info, cellHeight, density) else 1
        val maxSpanX = if (info != null) WidgetSizingUtils.getMaxSpanX(info, cellWidth, density) else HomeView.GRID_COLUMNS
        val maxSpanY = if (info != null) WidgetSizingUtils.getMaxSpanY(info, cellHeight, density) else HomeView.GRID_ROWS

        // Threshold detection: snap if moved more than 50% of cell width/height
        when (activeHandle) {
            HANDLE_RIGHT -> {
                if (canResizeHorizontal) {
                    val spanDelta = (currentDrx / (cellWidth * targetView.scaleX)).roundToInt()
                    newSpanX = (gestureInitialSpanX + spanDelta).coerceIn(minSpanX, maxSpanX)
                }
            }
            HANDLE_LEFT -> {
                if (canResizeHorizontal) {
                    val spanDelta = (-currentDrx / (cellWidth * targetView.scaleX)).roundToInt()
                    newSpanX = (gestureInitialSpanX + spanDelta).coerceIn(minSpanX, maxSpanX)
                    newCol = (gestureInitialCol.roundToInt() + gestureInitialSpanX - newSpanX)
                }
            }
            HANDLE_BOTTOM -> {
                if (canResizeVertical) {
                    val spanDelta = (currentDry / (cellHeight * targetView.scaleY)).roundToInt()
                    newSpanY = (gestureInitialSpanY + spanDelta).coerceIn(minSpanY, maxSpanY)
                }
            }
            HANDLE_TOP -> {
                if (canResizeVertical) {
                    val spanDelta = (-currentDry / (cellHeight * targetView.scaleY)).roundToInt()
                    newSpanY = (gestureInitialSpanY + spanDelta).coerceIn(minSpanY, maxSpanY)
                    newRow = (gestureInitialRow.roundToInt() + gestureInitialSpanY - newSpanY)
                }
            }
        }

        if (newSpanX != item.spanX || newSpanY != item.spanY || newCol != item.col.roundToInt() || newRow != item.row.roundToInt()) {
            if (activity.homeView.isSpanValid(item, newSpanX, newSpanY, newCol, newRow)) {
                item.spanX = newSpanX
                item.spanY = newSpanY
                item.col = newCol.toFloat()
                item.row = newRow.toFloat()
                activity.homeView.updateViewPosition(item, targetView)
            }
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).toDouble().pow(2.0) + (y1 - y2).toDouble().pow(2.0)).toFloat()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val MOVE_THRESHOLD_DP = 8f
        private const val SMOOTHING_FACTOR = 1.0f

        private const val HANDLE_TOP_LEFT = 0
        private const val HANDLE_TOP = 1
        private const val HANDLE_TOP_RIGHT = 2
        private const val HANDLE_RIGHT = 3
        private const val HANDLE_BOTTOM_RIGHT = 4
        private const val HANDLE_BOTTOM = 5
        private const val HANDLE_BOTTOM_LEFT = 6
        private const val HANDLE_LEFT = 7
        private const val HANDLE_ROTATE = 8
        private const val ACTION_MOVE = 9
        private const val ACTION_OUTSIDE = 10
    }
}
