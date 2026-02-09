package com.riprog.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.ArrayList

class HomeView(context: Context) : FrameLayout(context) {

    private val pagesContainer: LinearLayout
    private val pageIndicator: PageIndicator
    private val pages: MutableList<FrameLayout> = ArrayList()
    private val settingsManager: SettingsManager = SettingsManager(context)
    private var currentPage = 0
    private var accentColor = Color.WHITE
    private var model: LauncherModel? = null
    private var allApps: List<AppItem>? = null

    private var draggingView: View? = null
    private var lastX = 0f
    private var lastY = 0f
    private val edgeScrollHandler = Handler(Looper.getMainLooper())
    private var isEdgeScrolling = false
    private val edgeScrollRunnable = object : Runnable {
        override fun run() {
            if (draggingView != null) {
                if (lastX < width * 0.05f && currentPage > 0) {
                    scrollToPage(currentPage - 1)
                } else if (lastX > width * 0.95f && currentPage < pages.size - 1) {
                    scrollToPage(currentPage + 1)
                }
                edgeScrollHandler.postDelayed(this, 400)
            } else {
                isEdgeScrolling = false
            }
        }
    }

    init {
        pagesContainer = LinearLayout(context)
        pagesContainer.orientation = LinearLayout.HORIZONTAL
        pagesContainer.setPadding(0, dpToPx(48), 0, 0)
        addView(pagesContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        pageIndicator = PageIndicator(context)
        val indicatorParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        indicatorParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        indicatorParams.bottomMargin = dpToPx(80)
        addView(pageIndicator, indicatorParams)

        // Add initial pages
        addPage()
        addPage()

        addDrawerHint()
    }

    private fun addDrawerHint() {
        if (settingsManager.drawerOpenCount >= 5) return

        val hint = TextView(context)
        hint.setText(R.string.drawer_hint)
        hint.textSize = 12f
        hint.setTextColor(Color.GRAY and 0x80FFFFFF.toInt())
        hint.alpha = 0f
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.bottomMargin = dpToPx(120)
        addView(hint, lp)

        // Show occasionally (30% chance on startup)
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
        pages.add(page)
        pagesContainer.addView(page, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

    fun addItemView(item: HomeItem, view: View) {
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

    fun updateViewPosition(item: HomeItem, view: View) {
        val cellWidth = width / GRID_COLUMNS
        val cellHeight = height / GRID_ROWS

        if (cellWidth == 0 || cellHeight == 0) {
            post { updateViewPosition(item, view) }
            return
        }

        val lp: LayoutParams
        if (item.type == HomeItem.Type.WIDGET) {
            lp = LayoutParams(cellWidth * item.spanX, cellHeight * item.spanY)
        } else {
            // Apps and other items have standard size unless freeform allows scaling (not requested yet)
            val size = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            lp = LayoutParams(size * 2, size * 2) // Container size
        }
        view.layoutParams = lp

        view.x = item.col * cellWidth
        view.y = item.row * cellHeight
    }

    fun startDragging(v: View, x: Float, y: Float) {
        draggingView = v
        lastX = x
        lastY = y
        v.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.8f).setDuration(150).start()
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun handleDrag(x: Float, y: Float) {
        draggingView?.let {
            val dx = x - lastX
            val dy = y - lastY
            it.x = it.x + dx
            it.y = it.y + dy
            lastX = x
            lastY = y
            checkEdgeScroll(x)
        }
    }

    private fun checkEdgeScroll(x: Float) {
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

    fun endDragging() {
        draggingView?.let {
            it.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
            val item = it.tag as? HomeItem
            if (item != null) {
                snapToGrid(item, it)
            }
            draggingView = null
            isEdgeScrolling = false
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

    private fun snapToGrid(item: HomeItem, v: View) {
        val cellWidth = width / GRID_COLUMNS
        val cellHeight = height / GRID_ROWS

        if (settingsManager.isFreeformHome) {
            item.col = v.x / cellWidth.toFloat()
            item.row = v.y / cellHeight.toFloat()
        } else {
            item.col = Math.max(0, Math.min(GRID_COLUMNS - item.spanX, Math.round(v.x / cellWidth.toFloat()))).toFloat()
            item.row = Math.max(0, Math.min(GRID_ROWS - item.spanY, Math.round(v.y / cellHeight.toFloat()))).toFloat()

            v.animate()
                .x(item.col * cellWidth)
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
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (model != null && allApps != null) {
                    refreshIcons(model!!, allApps!!)
                }
            }
            .start()
        pageIndicator.setCurrentPage(page)
    }

    fun getCurrentPage(): Int = currentPage

    fun getPageCount(): Int = pages.size

    fun refreshIcons(model: LauncherModel, allApps: List<AppItem>) {
        this.model = model
        this.allApps = allApps
        val scale = settingsManager.iconScale
        val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val size = (baseSize * scale).toInt()

        for (page in pages) {
            for (i in 0 until page.childCount) {
                val view = page.getChildAt(i)
                val item = view.tag as? HomeItem
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
                            tv.textSize = 10 * scale
                        }

                        var app: AppItem? = null
                        for (a in allApps) {
                            if (a.packageName == item.packageName) {
                                app = a
                                break
                            }
                        }

                        if (iv != null && app != null) {
                            val finalApp = app
                            model.loadIcon(app) { bitmap ->
                                if (bitmap != null) {
                                    iv.setImageBitmap(bitmap)
                                    tv?.text = finalApp.label
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
            for (page in pages) {
                for (i in 0 until page.childCount) {
                    val v = page.getChildAt(i)
                    val item = v.tag as? HomeItem
                    if (item != null) {
                        if (!freeform) {
                            item.col = Math.max(0, Math.min(GRID_COLUMNS - item.spanX, Math.round(item.col))).toFloat()
                            item.row = Math.max(0, Math.min(GRID_ROWS - item.spanY, Math.round(item.row))).toFloat()
                        }
                        updateViewPosition(item, v)
                    }
                }
            }
            if (!freeform && context is MainActivity) {
                (context as MainActivity).saveHomeState()
            }
        }
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        pageIndicator.setAccentColor(color)
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private inner class PageIndicator(context: Context) : LinearLayout(context) {
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

    companion object {
        const val GRID_COLUMNS = 4
        const val GRID_ROWS = 6
    }
}
