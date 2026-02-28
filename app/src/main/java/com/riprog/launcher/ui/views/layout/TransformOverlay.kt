package com.riprog.launcher.ui.views.layout

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.logic.utils.WidgetSizingUtils
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.ui.views.home.HomeView
import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.R

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.withTranslation
import android.util.AttributeSet
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

class TransformOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var targetView: View? = null
    private var settingsManager: SettingsManager? = null
    private var onSaveListener: OnSaveListener? = null

    private var item: HomeItem? = null
    private var buttonsContainer: View? = null
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
    private var gestureInitialSpanX = 1f
    private var gestureInitialSpanY = 1f
    private var gestureInitialCol = 0f
    private var gestureInitialRow = 0f
    private var currentDrx = 0f
    private var currentDry = 0f
    private var hasPassedThreshold = false
    private var activeHandle = -1
    private var canResizeHorizontal = true
    private var canResizeVertical = true

    private var initialRotation: Float = 0f
    private var initialScaleX: Float = 1f
    private var initialScaleY: Float = 1f
    private var initialX: Float = 0f
    private var initialY: Float = 0f

    interface OnSaveListener {
        fun onMove(x: Float, y: Float)
        fun onMoveStart(x: Float, y: Float)
        fun onSave()
        fun onCancel()
        fun onRemove()
        fun onAppInfo()
        fun onCollision(otherView: View)
        fun findItemAt(x: Float, y: Float, exclude: View): View?
        fun onSnapToGrid(v: View, isResize: Boolean): Boolean
    }

    fun initData(targetView: View, settingsManager: SettingsManager, onSaveListener: OnSaveListener) {
        this.targetView = targetView
        this.settingsManager = settingsManager
        this.onSaveListener = onSaveListener

        initialRotation = targetView.rotation
        initialScaleX = targetView.scaleX
        initialScaleY = targetView.scaleY
        initialX = targetView.x
        initialY = targetView.y

        setup(targetView, settingsManager)
    }

    private fun setup(targetView: View, settingsManager: SettingsManager) {
        item = targetView.tag as? HomeItem
        if (targetView is AppWidgetHostView) {
            val info = targetView.appWidgetInfo
            if (info != null) {
                canResizeHorizontal = (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
                canResizeVertical = (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
            }
        }
        setWillNotDraw(false)
        setupButtons(settingsManager)
    }

    fun startImmediateDrag(x: Float, y: Float) {
        val tv = targetView ?: return
        val itm = item ?: return
        activeHandle = ACTION_MOVE
        initialTouchX = x
        initialTouchY = y
        lastTouchX = x
        lastTouchY = y
        gestureInitialScaleX = tv.scaleX
        gestureInitialScaleY = tv.scaleY
        gestureInitialX = tv.x
        gestureInitialY = tv.y

        // Ensure width and height are captured correctly even if not laid out
        gestureInitialWidth = if (tv.width > 0) tv.width else {
            val cw = (context as? MainActivity)?.homeView?.getCellWidth() ?: 0f
            (cw * itm.spanX).toInt()
        }
        gestureInitialHeight = if (tv.height > 0) tv.height else {
            val ch = (context as? MainActivity)?.homeView?.getCellHeight() ?: 0f
            (ch * itm.spanY).toInt()
        }

        gestureInitialBounds = contentBounds
        gestureInitialSpanX = itm.spanX
        gestureInitialSpanY = itm.spanY
        gestureInitialCol = itm.col
        gestureInitialRow = itm.row
        currentDrx = 0f
        currentDry = 0f
        hasPassedThreshold = true // Already long pressed
        onSaveListener?.onMoveStart(x, y)
    }

    private fun setupButtons(settingsManager: SettingsManager) {
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)

        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER
        container.setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
        container.background = ThemeUtils.getThemedSurface(context, settingsManager, 12f)

        addButton(container, R.string.action_remove, adaptiveColor) { onSaveListener?.onRemove() }
        if (item != null && (settingsManager.isFreeformHome || item?.type == HomeItem.Type.WIDGET)) {
            addButton(container, R.string.action_reset, adaptiveColor) { reset() }
        }
        addButton(container, R.string.action_save, adaptiveColor) { onSaveListener?.onSave() }

        if (item?.type == HomeItem.Type.APP) {
            addButton(container, R.string.action_app_info, adaptiveColor) { onSaveListener?.onAppInfo() }
        }

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.bottomMargin = dpToPx(48f)
        lp.leftMargin = dpToPx(24f)
        lp.rightMargin = dpToPx(24f)
        buttonsContainer = container
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
        val tv = targetView ?: return
        val itm = item ?: return
        val activity = context as? MainActivity ?: return

        tv.rotation = 0f
        tv.scaleX = 1.0f
        tv.scaleY = 1.0f
        tv.rotationX = 0f
        tv.rotationY = 0f

        val pageChanged = itm.page != itm.originalPage

        itm.col = itm.originalCol
        itm.row = itm.originalRow
        itm.spanX = itm.originalSpanX
        itm.spanY = itm.originalSpanY
        itm.page = itm.originalPage
        itm.rotation = 0f
        itm.scale = 1.0f
        itm.tiltX = 0f
        itm.tiltY = 0f

        if (pageChanged) {
            activity.homeView.removeItemView(itm)
            activity.renderHomeItem(itm)
        } else {
            activity.homeView.updateViewPosition(itm, tv)
        }

        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sm = settingsManager ?: return
        val tv = targetView ?: return
        val itm = item ?: return

        if (!sm.isFreeformHome && activeHandle != -1 && hasPassedThreshold) {
            drawGridOverlay(canvas)
            if (activeHandle == ACTION_MOVE) {
                drawSnapPreview(canvas)
            }
        }

        val isFreeform = sm.isFreeformHome
        val isWidget = itm.type == HomeItem.Type.WIDGET
        val sx = tv.scaleX
        val sy = tv.scaleY
        val r = if (isFreeform) tv.rotation else 0f

        val isEdgeResizing = activeHandle == HANDLE_TOP || activeHandle == HANDLE_BOTTOM || activeHandle == HANDLE_LEFT || activeHandle == HANDLE_RIGHT
        val cx = if (isEdgeResizing) gestureInitialX + gestureInitialWidth / 2f else tv.x + tv.pivotX
        val cy = if (isEdgeResizing) gestureInitialY + gestureInitialHeight / 2f else tv.y + tv.pivotY

        val bounds = if (activeHandle != -1 && gestureInitialBounds != null) gestureInitialBounds!! else contentBounds
        var left = (bounds.left - (if (isEdgeResizing) gestureInitialWidth / 2f else tv.pivotX)) * sx
        var top = (bounds.top - (if (isEdgeResizing) gestureInitialHeight / 2f else tv.pivotY)) * sy
        var right = (bounds.right - (if (isEdgeResizing) gestureInitialWidth / 2f else tv.pivotX)) * sx
        var bottom = (bounds.bottom - (if (isEdgeResizing) gestureInitialHeight / 2f else tv.pivotY)) * sy

        // Adjust guideline based on continuous displacement
        if (isEdgeResizing) {
            when (activeHandle) {
                HANDLE_RIGHT -> right += currentDrx
                HANDLE_LEFT -> left += currentDrx
                HANDLE_BOTTOM -> bottom += currentDry
                HANDLE_TOP -> top += currentDry
            }
        }

        canvas.withTranslation(cx, cy) {
            rotate(r)

            val foregroundColor = ThemeUtils.getAdaptiveColor(context, sm, false)
        paint.color = foregroundColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpToPx(1.2f).toFloat()
        paint.alpha = 80

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
        }
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float, isPrimary: Boolean, foregroundColor: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = if (isPrimary) 200 else 120
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.color = foregroundColor
        paint.strokeWidth = dpToPx(1.2f).toFloat()
        paint.alpha = if (isPrimary) 220 else 170
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private val contentBounds: RectF
        get() {
            val tv = targetView
            return if (tv != null) WidgetSizingUtils.getVisualBounds(tv) else RectF()
        }

    private fun drawGridOverlay(canvas: Canvas) {
        val sm = settingsManager ?: return
        val activity = context as? MainActivity ?: return
        val homeView = activity.homeView
        val cellWidth = homeView.getCellWidth()
        val cellHeight = homeView.getCellHeight()
        if (cellWidth <= 0 || cellHeight <= 0) return

        val columns = sm.columns
        val rows = HomeView.GRID_ROWS
        val density = resources.displayMetrics.density
        val horizontalPadding = HomeView.HORIZONTAL_PADDING_DP * density

        val homeLoc = IntArray(2)
        homeView.getLocationInWindow(homeLoc)
        val overlayLoc = IntArray(2)
        this.getLocationInWindow(overlayLoc)

        val offsetX = homeLoc[0] - overlayLoc[0] + horizontalPadding
        val offsetY = homeLoc[1] - overlayLoc[1] + homeView.recyclerView.paddingTop.toFloat()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpToPx(0.8f).toFloat()
        paint.color = ThemeUtils.getAdaptiveColor(context, sm, false)

        // Use very subtle lines for the grid
        for (i in 0..rows) {
            val y = offsetY + i * cellHeight
            paint.alpha = 30
            canvas.drawLine(offsetX, y, offsetX + columns * cellWidth, y, paint)
        }
        for (i in 0..columns) {
            val x = offsetX + i * cellWidth
            paint.alpha = 30
            canvas.drawLine(x, offsetY, x, offsetY + rows * cellHeight, paint)
        }

        // Draw slightly stronger corners for each cell to indicate target spots
        paint.alpha = 60
        val cornerSize = dpToPx(8f).toFloat()
        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val lx = offsetX + c * cellWidth
                val ty = offsetY + r * cellHeight
                val rx = lx + cellWidth
                val by = ty + cellHeight

                // Top-left corner
                canvas.drawLine(lx, ty, lx + cornerSize, ty, paint)
                canvas.drawLine(lx, ty, lx, ty + cornerSize, paint)
                // Top-right
                canvas.drawLine(rx, ty, rx - cornerSize, ty, paint)
                canvas.drawLine(rx, ty, rx, ty + cornerSize, paint)
                // Bottom-left
                canvas.drawLine(lx, by, lx + cornerSize, by, paint)
                canvas.drawLine(lx, by, lx, by - cornerSize, paint)
                // Bottom-right
                canvas.drawLine(rx, by, rx - cornerSize, by, paint)
                canvas.drawLine(rx, by, rx, by - cornerSize, paint)
            }
        }
    }

    private fun drawSnapPreview(canvas: Canvas) {
        val sm = settingsManager ?: return
        val tv = targetView ?: return
        val itm = item ?: return
        val activity = context as? MainActivity ?: return
        val homeView = activity.homeView
        val cellWidth = homeView.getCellWidth()
        val cellHeight = homeView.getCellHeight()
        if (cellWidth <= 0 || cellHeight <= 0) return

        val density = resources.displayMetrics.density
        val horizontalPadding = HomeView.HORIZONTAL_PADDING_DP * density

        val vBounds = WidgetSizingUtils.getVisualBounds(tv)

        val homeLoc = IntArray(2)
        homeView.getLocationInWindow(homeLoc)
        val overlayLoc = IntArray(2)
        this.getLocationInWindow(overlayLoc)

        val offsetX = homeLoc[0] - overlayLoc[0] + horizontalPadding
        val offsetY = homeLoc[1] - overlayLoc[1] + homeView.recyclerView.paddingTop.toFloat()

        // Calculate where it would snap
        val midX = tv.x + vBounds.centerX()
        val midY = tv.y + vBounds.centerY()

        val columns = sm.columns
        val targetCol = ((midX - offsetX - (cellWidth * itm.spanX / 2f)) / cellWidth).roundToInt()
            .coerceIn(0, columns - itm.spanX.toInt())
        val targetRow = ((midY - offsetY - (cellHeight * itm.spanY / 2f)) / cellHeight).roundToInt()
            .coerceIn(0, HomeView.GRID_ROWS - itm.spanY.toInt())

        val snapX = offsetX + targetCol * cellWidth
        val snapY = offsetY + targetRow * cellHeight
        val snapW = itm.spanX * cellWidth
        val snapH = itm.spanY * cellHeight

        paint.style = Paint.Style.FILL
        paint.color = ThemeUtils.getAdaptiveColor(context, sm, false)
        paint.alpha = 40
        canvas.drawRoundRect(snapX, snapY, snapX + snapW, snapY + snapH, dpToPx(8f).toFloat(), dpToPx(8f).toFloat(), paint)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tv = targetView ?: return false
        val itm = item ?: return false
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                activeHandle = findHandle(x, y)
                if (activeHandle != ACTION_OUTSIDE) {
                    if (activeHandle == ACTION_MOVE) {
                        onSaveListener?.onMoveStart(x, y)
                    }
                    initialTouchX = x
                    initialTouchY = y
                    lastTouchX = x
                    lastTouchY = y
                    gestureInitialScaleX = tv.scaleX
                    gestureInitialScaleY = tv.scaleY
                    gestureInitialX = tv.x
                    gestureInitialY = tv.y
                    gestureInitialWidth = tv.width
                    gestureInitialHeight = tv.height
                    gestureInitialBounds = contentBounds
                    gestureInitialSpanX = itm.spanX
                    gestureInitialSpanY = itm.spanY
                    gestureInitialCol = itm.col
                    gestureInitialRow = itm.row
                    currentDrx = 0f
                    currentDry = 0f
                    hasPassedThreshold = false
                    return true
                } else {
                    val other = onSaveListener?.findItemAt(x, y, tv)
                    if (other != null) {
                        onSaveListener?.onCollision(other)
                        return true
                    }
                    onSaveListener?.onSave()
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
                        buttonsContainer?.visibility = if (activeHandle == ACTION_MOVE) View.GONE else View.VISIBLE
                        handleInteraction(x, y)
                    }
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                buttonsContainer?.visibility = View.VISIBLE
                if (activeHandle != -1 && activeHandle != ACTION_OUTSIDE && hasPassedThreshold) {
                    val midX = tv.x + tv.width / 2f
                    val midY = tv.y + tv.height / 2f

                    val isResize = activeHandle == HANDLE_TOP || activeHandle == HANDLE_BOTTOM ||
                                   activeHandle == HANDLE_LEFT || activeHandle == HANDLE_RIGHT ||
                                   activeHandle == HANDLE_TOP_LEFT || activeHandle == HANDLE_TOP_RIGHT ||
                                   activeHandle == HANDLE_BOTTOM_LEFT || activeHandle == HANDLE_BOTTOM_RIGHT

                    onSaveListener?.onSnapToGrid(tv, isResize)

                    if (activeHandle == ACTION_MOVE) {
                        val other = onSaveListener?.findItemAt(midX, midY, tv)
                        if (other != null) {
                            onSaveListener?.onCollision(other)
                        }
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
        val sm = settingsManager ?: return ACTION_OUTSIDE
        val tv = targetView ?: return ACTION_OUTSIDE
        val itm = item ?: return ACTION_OUTSIDE
        val isFreeform = sm.isFreeformHome
        val isWidget = itm.type == HomeItem.Type.WIDGET
        val sx = tv.scaleX
        val sy = tv.scaleY
        val cx = tv.x + tv.pivotX
        val cy = tv.y + tv.pivotY

        val angle = if (isFreeform) Math.toRadians((-tv.rotation).toDouble()) else 0.0
        val rx = (cos(angle) * (tx - cx) - sin(angle) * (ty - cy)).toFloat()
        val ry = (sin(angle) * (tx - cx) + cos(angle) * (ty - cy)).toFloat()

        val bounds = WidgetSizingUtils.getVisualBounds(tv)
        val left = (bounds.left - tv.pivotX) * sx
        val top = (bounds.top - tv.pivotY) * sy
        val right = (bounds.right - tv.pivotX) * sx
        val bottom = (bounds.bottom - tv.pivotY) * sy

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
        val tv = targetView ?: return
        val sm = settingsManager ?: return
        val isFreeform = sm.isFreeformHome
        val sx = gestureInitialScaleX
        val sy = gestureInitialScaleY
        val cx = gestureInitialX + tv.pivotX
        val cy = gestureInitialY + tv.pivotY

        if (activeHandle == ACTION_MOVE) {
            val newX = gestureInitialX + (tx - initialTouchX)
            val newY = gestureInitialY + (ty - initialTouchY)

            // Smooth movement during drag for "light" feel
            tv.x = newX
            tv.y = newY
            onSaveListener?.onMove(tx, ty)
        } else if (activeHandle == HANDLE_ROTATE) {
            val angle = Math.toDegrees(atan2((ty - cy).toDouble(), (tx - cx).toDouble())) + 90
            var targetR = angle.toFloat()
            val currentR = tv.rotation
            while (targetR - currentR > 180) targetR -= 360f
            while (targetR - currentR < -180) targetR += 360f
            tv.rotation = currentR + (targetR - currentR) * SMOOTHING_FACTOR
        } else if (activeHandle == HANDLE_TOP || activeHandle == HANDLE_BOTTOM || activeHandle == HANDLE_LEFT || activeHandle == HANDLE_RIGHT) {
            handleEdgeResize(tx, ty)
        } else {
            // Corner-based scaling (freeform only)
            if (!isFreeform) return

            val initialDist = dist(initialTouchX, initialTouchY, cx, cy)
            val currDist = dist(tx, ty, cx, cy)
            if (initialDist > 0) {
                val factor = currDist / initialDist
                tv.scaleX = sx * factor
                tv.scaleY = sy * factor
            }
        }
    }

    private fun handleEdgeResize(tx: Float, ty: Float) {
        val tv = targetView ?: return
        val activity = context as? MainActivity ?: return
        val cellWidth = activity.homeView.getCellWidth()
        val cellHeight = activity.homeView.getCellHeight()
        if (cellWidth <= 0 || cellHeight <= 0) return

        // Use local coordinates for better accuracy if rotated
        val rotAngle = Math.toRadians((-tv.rotation).toDouble()).toFloat()
        val cx = gestureInitialX + tv.pivotX
        val cy = gestureInitialY + tv.pivotY
        val rx = (cos(rotAngle.toDouble()) * (tx - cx) - sin(rotAngle.toDouble()) * (ty - cy)).toFloat()
        val ry = (sin(rotAngle.toDouble()) * (tx - cx) + cos(rotAngle.toDouble()) * (ty - cy)).toFloat()
        val irx = (cos(rotAngle.toDouble()) * (initialTouchX - cx) - sin(rotAngle.toDouble()) * (initialTouchY - cy)).toFloat()
        val iry = (sin(rotAngle.toDouble()) * (initialTouchX - cx) + cos(rotAngle.toDouble()) * (initialTouchY - cy)).toFloat()

        currentDrx = rx - irx
        currentDry = ry - iry

        // Follow finger exactly without real-time grid snapping
        val lp = tv.layoutParams
        when (activeHandle) {
            HANDLE_RIGHT -> {
                lp.width = (gestureInitialWidth + currentDrx).toInt().coerceAtLeast(dpToPx(40f))
            }
            HANDLE_LEFT -> {
                lp.width = (gestureInitialWidth - currentDrx).toInt().coerceAtLeast(dpToPx(40f))
                tv.x = gestureInitialX + currentDrx
            }
            HANDLE_BOTTOM -> {
                lp.height = (gestureInitialHeight + currentDry).toInt().coerceAtLeast(dpToPx(40f))
            }
            HANDLE_TOP -> {
                lp.height = (gestureInitialHeight - currentDry).toInt().coerceAtLeast(dpToPx(40f))
                tv.y = gestureInitialY + currentDry
            }
        }
        tv.layoutParams = lp
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
