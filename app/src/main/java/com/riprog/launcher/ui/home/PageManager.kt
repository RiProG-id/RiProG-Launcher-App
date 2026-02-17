package com.riprog.launcher.ui.home

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.local.prefs.LauncherPreferences

class PageManager(
    private val container: LinearLayout,
    private val indicator: PageIndicator,
    private val settingsManager: LauncherPreferences,
    private val onPageChanged: () -> Unit
) {
    private val pages = mutableListOf<FrameLayout>()
    private var currentPage = 0

    init {
        val savedPageCount = settingsManager.pageCount
        for (i in 0 until savedPageCount) {
            addPage()
        }
    }

    fun addPage(): Int {
        val page = FrameLayout(container.context)
        pages.add(page)
        val containerWidth = container.width
        val lp = LinearLayout.LayoutParams(
            if (containerWidth > 0) containerWidth else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(page, lp)

        if (containerWidth <= 0) {
            container.post {
                val currentWidth = container.width
                if (currentWidth > 0) {
                    val pageLp = page.layoutParams as LinearLayout.LayoutParams
                    if (pageLp.width != currentWidth) {
                        pageLp.width = currentWidth
                        page.layoutParams = pageLp
                    }
                }
            }
        }

        indicator.setPageCount(pages.size)
        indicator.setCurrentPage(currentPage)
        return pages.size - 1
    }

    fun addPageAtIndex(index: Int) {
        val page = FrameLayout(container.context)
        pages.add(index, page)
        val containerWidth = container.width
        val lp = LinearLayout.LayoutParams(
            if (containerWidth > 0) containerWidth else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(page, index, lp)

        if (containerWidth <= 0) {
            container.post {
                val currentWidth = container.width
                if (currentWidth > 0) {
                    val pageLp = page.layoutParams as LinearLayout.LayoutParams
                    if (pageLp.width != currentWidth) {
                        pageLp.width = currentWidth
                        page.layoutParams = pageLp
                    }
                }
            }
        }

        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v.tag as? HomeItem
                item?.page = i
            }
        }
        indicator.setPageCount(pages.size)
        indicator.setCurrentPage(currentPage)
    }

    fun removePage(index: Int, onItemsRemoved: (List<HomeItem>) -> Unit) {
        if (index < 0 || index >= pages.size || pages.size <= 1) return
        val oldPageCount = pages.size

        val page = pages.removeAt(index)
        container.removeView(page)

        if (currentPage >= pages.size) {
            currentPage = pages.size - 1
        }

        cleanupEmptyPages()
        if (container.context is MainActivity) {
            settingsManager.removePageData(index, oldPageCount)
            scrollToPage(currentPage)
        }
    }

    fun cleanupEmptyPages() {
        for (i in pages.indices) {
            val p = pages[i]
            for (j in 0 until p.childCount) {
                val v = p.getChildAt(j)
                val item = v?.tag as? HomeItem
                item?.page = i
            }
        }
        indicator.setPageCount(pages.size)
        indicator.setCurrentPage(currentPage)
    }

    fun scrollToPage(page: Int) {
        val n = pages.size
        val width = container.width
        if (width <= 0) {
            container.post { scrollToPage(page) }
            return
        }

        val targetPage = if (n > 0) Math.max(0, Math.min(n - 1, page)) else 0
        currentPage = targetPage

        val pageWidth = width
        val targetX = currentPage * pageWidth

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val lp = child.layoutParams as LinearLayout.LayoutParams
            if (lp.width != pageWidth) {
                lp.width = pageWidth
                child.layoutParams = lp
            }
        }

        container.animate()
            .translationX((-targetX).toFloat())
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                onPageChanged()
            }
            .start()
        indicator.setCurrentPage(currentPage)
        indicator.animate().alpha(0.5f).setDuration(100).withEndAction {
            indicator.animate().alpha(1f).setDuration(100).start()
        }.start()
    }

    fun getCurrentPage(): Int = currentPage
    fun getPageCount(): Int = pages.size
    fun getPageAt(index: Int): FrameLayout? = if (index in pages.indices) pages[index] else null
    fun getAllPages(): List<FrameLayout> = pages
}
