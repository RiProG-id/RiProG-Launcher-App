package com.riprog.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.riprog.launcher.manager.GridManager
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.utils.SettingsManager
import com.riprog.launcher.utils.ThemeUtils

class TransformOverlay(
    context: Context,
    private val targetView: View,
    private val settingsManager: SettingsManager,
    private val onSaveListener: OnSaveListener?
) : FrameLayout(context) {

    private val gridManager = GridManager(settingsManager.columns)
    private val item: HomeItem = targetView.tag as HomeItem
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleSize: Float = dpToPx(12).toFloat()
    private val rotationHandleDist: Float = dpToPx(50).toFloat()

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
    }

    init {
        if (targetView is android.appwidget.AppWidgetHostView) {
            val info = targetView.appWidgetInfo
            if (info != null) {
                canResizeHorizontal = (info.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
                canResizeVertical = (info.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
            }
        }
        setWillNotDraw(false)
        setupButtons()
    }

    fun startDirectMove(x: Float, y: Float) {
        onSaveListener?.onMoveStart(x, y)
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
        hasPassedThreshold = true
        invalidate()
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
                if (child.visibility == VISIBLE) {
                    minX = Math.min(minX, child.x)
                    minY = Math.min(minY, child.y)
                    maxX = Math.max(maxX, child.x + child.width)
                    maxY = Math.max(maxY, child.y + child.height)
                    hasVisibleChildren = true
                }
            }

            if (!hasVisibleChildren) {
                return RectF(0f, 0f, targetView.width.toFloat(), targetView.height.toFloat())
            }
            return RectF(minX, minY, maxX, maxY)
        }

    private fun setupButtons() {
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)

        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER
        container.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
        container.background = ThemeUtils.getGlassDrawable(context, settingsManager, 12f)
        container.setOnClickListener { }

        val btnRemove = TextView(context)
        btnRemove.text = "Remove"
        btnRemove.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        btnRemove.setTextColor(adaptiveColor)
        btnRemove.textSize = 11f
        btnRemove.setTypeface(null, android.graphics.Typeface.BOLD)
        btnRemove.gravity = Gravity.CENTER
        btnRemove.setOnClickListener { onSaveListener?.onRemove() }
        container.addView(btnRemove, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f))

        val btnReset = TextView(context)
        btnReset.text = "Reset"
        btnReset.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        btnReset.setTextColor(adaptiveColor)
        btnReset.textSize = 11f
        btnReset.setTypeface(null, android.graphics.Typeface.BOLD)
        btnReset.gravity = Gravity.CENTER
        btnReset.setOnClickListener { reset() }
        container.addView(btnReset, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f))

        val btnSave = TextView(context)
        btnSave.text = "Save"
        btnSave.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        btnSave.setTextColor(adaptiveColor)
        btnSave.textSize = 11f
        btnSave.setTypeface(null, android.graphics.Typeface.BOLD)
        btnSave.gravity = Gravity.CENTER
        btnSave.setOnClickListener { save() }
        container.addView(btnSave, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f))

        if (item.type != HomeItem.Type.WIDGET && item.type != HomeItem.Type.FOLDER) {
            val btnInfo = TextView(context)
            btnInfo.text = "App Info"
            btnInfo.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            btnInfo.setTextColor(adaptiveColor)
            btnInfo.textSize = 11f
            btnInfo.setTypeface(null, android.graphics.Typeface.BOLD)
            btnInfo.gravity = Gravity.CENTER
            btnInfo.setOnClickListener { onSaveListener?.onAppInfo() }
            container.addView(btnInfo, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f))
        }

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.bottomMargin = dpToPx(48)
        lp.leftMargin = dpToPx(24)
        lp.rightMargin = dpToPx(24)
        addView(container, lp)
    }

    private fun reset() {
        targetView.rotation = initialRotation
        targetView.scaleX = initialScaleX
        targetView.scaleY = initialScaleY
        targetView.x = initialX
        targetView.y = initialY
        invalidate()
    }

    private fun save() {
        onSaveListener?.onSave()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (targetView == null) return

        val isFreeform = settingsManager.isFreeformHome

        val sx = targetView.scaleX
        val sy = targetView.scaleY
        val r = if (isFreeform) targetView.rotation else 0f

        val cx = targetView.x + targetView.pivotX
        val cy = targetView.y + targetView.pivotY

        val bounds = if (activeHandle != -1 && gestureInitialBounds != null) gestureInitialBounds!! else contentBounds
        val left = (bounds.left - targetView.pivotX) * sx
        val top = (bounds.top - targetView.pivotY) * sy
        val right = (bounds.right - targetView.pivotX) * sx
        val bottom = (bounds.bottom - targetView.pivotY) * sy

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(r)

        val foregroundColor = ThemeUtils.getAdaptiveColor(context, settingsManager, false)

        paint.color = foregroundColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpToPx(1).toFloat()
        paint.alpha = 60
        canvas.drawRect(left, top, right, bottom, paint)

        val hs = handleSize / 2f

        if (isFreeform) {
            drawHandle(canvas, left, top, hs, true, foregroundColor)
            drawHandle(canvas, right, top, hs, true, foregroundColor)
            drawHandle(canvas, right, bottom, hs, true, foregroundColor)
            drawHandle(canvas, left, bottom, hs, true, foregroundColor)

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
        paint.strokeWidth = dpToPx(1).toFloat()
        paint.alpha = if (isPrimary) 200 else 150
        canvas.drawCircle(cx, cy, radius, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = findHandle(x, y)
                if (activeHandle == ACTION_MOVE) {
                    onSaveListener?.onMoveStart(x, y)
                }
                if (activeHandle == ACTION_OUTSIDE) {
                    if (onSaveListener != null) {
                        val other = onSaveListener.findItemAt(x, y, targetView)
                        if (other != null) {
                            onSaveListener.onCollision(other)
                            return true
                        }
                        onSaveListener.onSave()
                    }
                    return false
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
                hasPassedThreshold = false
                return activeHandle != -1
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != -1) {
                    if (!hasPassedThreshold) {
                        val threshold = dpToPx(MOVE_THRESHOLD_DP.toInt()).toFloat()
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
                if (activeHandle == ACTION_MOVE) {
                    val midX = targetView.x + targetView.width / 2f
                    val midY = targetView.y + targetView.height / 2f
                    val other = onSaveListener?.findItemAt(midX, midY, targetView)
                    if (other != null && item.type == HomeItem.Type.APP) {
                        val otherItem = other.tag as? HomeItem
                        if (otherItem != null && (otherItem.type == HomeItem.Type.APP || otherItem.type == HomeItem.Type.FOLDER)) {
                            onSaveListener?.onSave()
                            activeHandle = -1
                            hasPassedThreshold = false
                            return true
                        }
                    }
                }

                if (activeHandle != -1 && activeHandle != ACTION_MOVE && activeHandle != HANDLE_ROTATE && activeHandle != ACTION_OUTSIDE) {
                    if (targetView is android.appwidget.AppWidgetHostView) {
                        val lp = targetView.layoutParams
                        val dw = (lp.width / resources.displayMetrics.density).toInt()
                        val dh = (lp.height / resources.displayMetrics.density).toInt()
                        targetView.updateAppWidgetSize(null, dw, dh, dw, dh)
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
        val sx = targetView.scaleX
        val sy = targetView.scaleY

        val cx = targetView.x + targetView.pivotX
        val cy = targetView.y + targetView.pivotY

        val angle = if (isFreeform) Math.toRadians((-targetView.rotation).toDouble()) else 0.0
        val rx = (Math.cos(angle) * (tx - cx) - Math.sin(angle) * (ty - cy)).toFloat()
        val ry = (Math.sin(angle) * (tx - cx) + Math.cos(angle) * (ty - cy)).toFloat()

        val bounds = contentBounds

        val left = (bounds.left - targetView.pivotX) * sx
        val top = (bounds.top - targetView.pivotY) * sy
        val right = (bounds.right - targetView.pivotX) * sx
        val bottom = (bounds.bottom - targetView.pivotY) * sy

        val hs = dpToPx(24).toFloat()

        if ((isFreeform || item.type == HomeItem.Type.WIDGET || item.type == HomeItem.Type.FOLDER) &&
            dist(rx, ry, (left + right) / 2f, top - rotationHandleDist) < hs
        ) return HANDLE_ROTATE

        if (isFreeform && canResizeHorizontal && canResizeVertical) {
            if (dist(rx, ry, left, top) < hs) return HANDLE_TOP_LEFT
            if (dist(rx, ry, right, top) < hs) return HANDLE_TOP_RIGHT
            if (dist(rx, ry, right, bottom) < hs) return HANDLE_BOTTOM_RIGHT
            if (dist(rx, ry, left, bottom) < hs) return HANDLE_BOTTOM_LEFT
        }

        if (rx in left..right && ry in top..bottom) return ACTION_MOVE

        return ACTION_OUTSIDE
    }

    private fun handleInteraction(tx: Float, ty: Float) {
        val isFreeform = settingsManager.isFreeformHome
        val sx = gestureInitialScaleX
        val sy = gestureInitialScaleY
        val cx = gestureInitialX + targetView.pivotX
        val cy = gestureInitialY + targetView.pivotY

        if (activeHandle == ACTION_MOVE) {
            var newX = gestureInitialX + (tx - initialTouchX)
            var newY = gestureInitialY + (ty - initialTouchY)

            newX = Math.max(0f, Math.min(newX, (width - targetView.width).toFloat()))
            newY = Math.max(0f, Math.min(newY, (height - targetView.height).toFloat()))

            if (!isFreeform) {
                val cellWidth = gridManager.getCellWidth(width)
                val cellHeight = gridManager.getCellHeight(height)
                if (cellWidth > 0 && cellHeight > 0) {
                    newX = Math.round(newX / cellWidth).toFloat() * cellWidth
                    newY = Math.round(newY / cellHeight).toFloat() * cellHeight
                }
            }

            targetView.x = newX
            targetView.y = newY
            onSaveListener?.onMove(tx, ty)
        } else if (activeHandle == HANDLE_ROTATE && (isFreeform || item.type == HomeItem.Type.FOLDER || item.type == HomeItem.Type.WIDGET)) {
            val angle = Math.toDegrees(Math.atan2((ty - cy).toDouble(), (tx - cx).toDouble())) + 90
            var targetR = angle.toFloat()
            val currentR = targetView.rotation

            while (targetR - currentR > 180) targetR -= 360f
            while (targetR - currentR < -180) targetR += 360f

            targetView.rotation = currentR + (targetR - currentR) * SMOOTHING_FACTOR
        } else {
            val rotAngle = Math.toRadians((-targetView.rotation).toDouble())
            val rx = (Math.cos(rotAngle) * (tx - cx) - Math.sin(rotAngle) * (ty - cy)).toFloat()
            val ry = (Math.sin(rotAngle) * (tx - cx) + Math.cos(rotAngle) * (ty - cy)).toFloat()

            val bounds = gestureInitialBounds ?: contentBounds
            val halfContentW = bounds.width() / 2f
            val halfContentH = bounds.height() / 2f

            var newScaleX = sx
            var newScaleY = sy

            if (isFreeform) {
                val initialDist = dist(initialTouchX, initialTouchY, cx, cy)
                val currDist = dist(tx, ty, cx, cy)
                if (initialDist > 0) {
                    var factor = currDist / initialDist

                    val minFactor = 0.2f / Math.min(sx, sy)
                    val maxFactor = 5.0f / Math.max(sx, sy)
                    factor = Math.max(minFactor, Math.min(maxFactor, factor))

                    if (gestureInitialWidth > 0 && gestureInitialHeight > 0) {
                        val maxSX = Math.min(2 * cx, 2 * (width - cx)) * sx / gestureInitialWidth.toFloat()
                        val maxSY = Math.min(2 * cy, 2 * (height - cy)) * sy / gestureInitialHeight.toFloat()
                        val boundFactor = Math.min(maxSX / sx, maxSY / sy)
                        factor = Math.min(factor, boundFactor)
                    }

                    newScaleX = sx * factor
                    newScaleY = sy * factor
                }
            } else {
                when (activeHandle) {
                    HANDLE_TOP, HANDLE_BOTTOM -> {
                        if (halfContentH > 0 && canResizeVertical)
                            newScaleY = Math.max(0.2f, Math.min(5.0f, Math.abs(ry) / halfContentH))
                    }
                    HANDLE_LEFT, HANDLE_RIGHT -> {
                        if (halfContentW > 0 && canResizeHorizontal)
                            newScaleX = Math.max(0.2f, Math.min(5.0f, Math.abs(rx) / halfContentW))
                    }
                    HANDLE_TOP_LEFT, HANDLE_TOP_RIGHT, HANDLE_BOTTOM_LEFT, HANDLE_BOTTOM_RIGHT -> {
                        if (canResizeHorizontal && canResizeVertical) {
                            val initialDist = dist(initialTouchX, initialTouchY, cx, cy)
                            val currDist = dist(tx, ty, cx, cy)
                            if (initialDist > 0) {
                                val factor = currDist / initialDist
                                newScaleX = sx * factor
                                newScaleY = sy * factor
                            }
                        }
                    }
                }

                if (gestureInitialWidth > 0 && gestureInitialHeight > 0) {
                    val maxSX = Math.min(5.0f, Math.min(2 * cx, 2 * (width - cx)) * sx / gestureInitialWidth.toFloat())
                    val maxSY = Math.min(5.0f, Math.min(2 * cy, 2 * (height - cy)) * sy / gestureInitialHeight.toFloat())
                    newScaleX = Math.min(newScaleX, maxSX)
                    newScaleY = Math.min(newScaleY, maxSY)
                }
            }

            if (!isFreeform) {
                val cellWidth = gridManager.getCellWidth(width)
                val cellHeight = gridManager.getCellHeight(height)
                if (cellWidth > 0 && cellHeight > 0) {
                    var targetW = newScaleX * (gestureInitialWidth / gestureInitialScaleX)
                    var targetH = newScaleY * (gestureInitialHeight / gestureInitialScaleY)
                    targetW = Math.max(cellWidth.toFloat(), Math.round(targetW / cellWidth).toFloat() * cellWidth)
                    targetH = Math.max(cellHeight.toFloat(), Math.round(targetH / cellHeight).toFloat() * cellHeight)
                    newScaleX = targetW * gestureInitialScaleX / gestureInitialWidth.toFloat()
                    newScaleY = targetH * gestureInitialScaleY / gestureInitialHeight.toFloat()
                }
            } else {
                newScaleX = Math.round(newScaleX * 100f) / 100.0f
                newScaleY = Math.round(newScaleY * 100f) / 100.0f
            }

            targetView.scaleX = newScaleX
            targetView.scaleY = newScaleY
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0)).toFloat()
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
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
