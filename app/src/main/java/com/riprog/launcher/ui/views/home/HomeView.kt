package com.riprog.launcher.ui.views.home

import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.callback.PageActionCallback
import com.riprog.launcher.R

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
import java.util.ArrayList
import java.util.HashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class HomeView(context: Context) : FrameLayout(context), PageActionCallback {
    private val pagesContainer: LinearLayout
    private val pageIndicator: PageIndicator
    private val pages: MutableList<FrameLayout> = ArrayList()
    private val settingsManager: SettingsManager = SettingsManager(context)
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
                    if (currentPage < pages.size - 1) {
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
        val page = FrameLayout(context)
        page.clipChildren = false
        page.clipToPadding = false
        pages.add(index, page)
        pagesContainer.addView(
            page, index, LinearLayout.LayoutParams(
                pageWidth, LayoutParams.MATCH_PARENT
            )
        )


        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v.tag as HomeItem?
                if (item != null) item.page = i
            }
        }
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

    init {
        clipChildren = false
        clipToPadding = false

        pagesContainer = LinearLayout(context)
        pagesContainer.orientation = LinearLayout.HORIZONTAL
        pagesContainer.setPadding(0, dpToPx(48), 0, 0)
        pagesContainer.clipChildren = false
        pagesContainer.clipToPadding = false
        addView(pagesContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        pageIndicator = PageIndicator(context)
        val indicatorParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        indicatorParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        indicatorParams.bottomMargin = dpToPx(80)
        addView(pageIndicator, indicatorParams)


        addPage()
        addPage()

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
        val page = FrameLayout(context)
        page.clipChildren = false
        page.clipToPadding = false
        pages.add(page)
        pagesContainer.addView(
            page, LinearLayout.LayoutParams(
                pageWidth, LayoutParams.MATCH_PARENT
            )
        )
        pageIndicator.setPageCount(pages.size)
        pageIndicator.setCurrentPage(currentPage)
    }

    private fun handleEdgePageCreation() {
        if (edgeHoldStart == 0L) {
            edgeHoldStart = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - edgeHoldStart > 1000) {
            if (lastX < width * 0.05f && currentPage == 0) {
                addPageAtIndex(0)
                scrollToPage(0)
            } else if (lastX > width * 0.95f && currentPage == pages.size - 1) {
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
    }

    fun updateViewPosition(item: HomeItem, view: View) {
        val cellWidth = width / GRID_COLUMNS
        val cellHeight = (height - dpToPx(48)) / GRID_ROWS

        if (cellWidth == 0 || cellHeight <= 0) {
            post { updateViewPosition(item, view) }
            return
        }

        val lp: LayoutParams = if (item.type == HomeItem.Type.WIDGET) {
            LayoutParams(cellWidth * item.spanX, cellHeight * item.spanY)
        } else {

            val size = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            LayoutParams(size * 2, size * 2)
        }
        view.layoutParams = lp

        view.x = item.col * cellWidth
        view.y = item.row * cellHeight

        view.rotation = item.rotation
        view.scaleX = item.scale
        view.scaleY = item.scale
        view.rotationX = item.tiltX
        view.rotationY = item.tiltY
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
                absX += p.left + pagesContainer.translationX
                absY += p.top + pagesContainer.translationY
                (p as ViewGroup).removeView(v)
            }
            addView(v)
            v.x = absX
            v.y = absY
        }

        v.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.8f).setDuration(150).start()
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
        if (draggingView != null) {
            draggingView!!.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
            val item = draggingView!!.tag as HomeItem?
            if (item != null) {
                val absX = draggingView!!.x
                val absY = draggingView!!.y

                item.page = currentPage
                removeView(draggingView)
                addItemView(item, draggingView)

                draggingView!!.x = absX - (pages[currentPage].left + pagesContainer.translationX)
                draggingView!!.y = absY - (pages[currentPage].top + pagesContainer.translationY)

                snapToGrid(item, draggingView!!)
            }
            draggingView = null
            isEdgeScrolling = false
            edgeHoldStart = 0
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
            if (model != null && allApps != null) {
                refreshIcons(model!!, allApps!!)
            }
        }
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
        val page = pages.removeAt(index)
        pagesContainer.removeView(page)

        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v.tag as HomeItem?
                if (item != null) item.page = i
            }
        }
        pageIndicator.setPageCount(pages.size)
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

    fun cancelDragging() {
        draggingView = null
        isEdgeScrolling = false
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
    }

    private fun snapToGrid(item: HomeItem, v: View) {
        val cellWidth = width / GRID_COLUMNS
        val cellHeight = (height - dpToPx(48)) / GRID_ROWS

        if (context is MainActivity) {
            val activity = context as MainActivity
            val midX = v.x + v.width / 2f
            val midY = v.y + v.height / 2f

            var otherView: View? = null
            val currentPageLayout = pages[currentPage]
            for (i in 0 until currentPageLayout.childCount) {
                val child = currentPageLayout.getChildAt(i)
                if (child === v) continue
                if (midX >= child.x && midX <= child.x + child.width &&
                    midY >= child.y && midY <= child.y + child.height
                ) {
                    otherView = child
                    break
                }
            }

            if (otherView != null && item.type == HomeItem.Type.APP) {
                val otherItem = otherView.tag as HomeItem?
                if (otherItem != null) {
                    if (otherItem.type == HomeItem.Type.APP) {
                        activity.folderManager.mergeToFolder(otherItem, item, activity.homeItems)
                        return
                    } else if (otherItem.type == HomeItem.Type.FOLDER) {
                        activity.folderManager.addToFolder(otherItem, item, activity.homeItems)
                        return
                    }
                }
            }
        }

        if (settingsManager.isFreeformHome) {
            item.col = v.x / cellWidth.toFloat()
            item.row = v.y / cellHeight.toFloat()
            item.rotation = v.rotation
            item.scale = v.scaleX
            item.tiltX = v.rotationX
            item.tiltY = v.rotationY
        } else {
            item.col = max(0, min(GRID_COLUMNS - item.spanX, (v.x / cellWidth.toFloat()).roundToInt())).toFloat()
            item.row = max(0, min(GRID_ROWS - item.spanY, (v.y / cellHeight.toFloat()).roundToInt())).toFloat()
            item.rotation = 0f
            item.scale = 1.0f
            item.tiltX = 0f
            item.tiltY = 0f

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
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                if (model != null && allApps != null) {
                    refreshIcons(model!!, allApps!!)
                }
            }
            .start()
        pageIndicator.setCurrentPage(page)
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
                        val container = view
                        val iv = findImageView(container)
                        val tv = findTextView(container)

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
            for (page in pages) {
                for (i in 0 until page.childCount) {
                    val v = page.getChildAt(i)
                    val item = v.tag as HomeItem?
                    if (item != null) {
                        if (!freeform) {
                            item.col = max(0, min(GRID_COLUMNS - item.spanX, item.col.roundToInt())).toFloat()
                            item.row = max(0, min(GRID_ROWS - item.spanY, item.row.roundToInt())).toFloat()
                            item.rotation = 0f
                            item.scale = 1.0f
                            item.tiltX = 0f
                            item.tiltY = 0f
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
