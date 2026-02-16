package com.riprog.launcher.ui.home

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.riprog.launcher.MainActivity
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.ui.PageIndicator
import com.riprog.launcher.utils.SettingsManager

class PageManager(
    private val container: LinearLayout,
    private val indicator: PageIndicator,
    private val settingsManager: SettingsManager,
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

    fun addPage() {
        val page = FrameLayout(container.context)
        pages.add(page)
        container.addView(page, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        indicator.setPageCount(pages.size)
        indicator.setCurrentPage(currentPage)
    }

    fun addPageAtIndex(index: Int) {
        val page = FrameLayout(container.context)
        pages.add(index, page)
        container.addView(page, index, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

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
        if (n <= 1) {
            currentPage = 0
            container.translationX = 0f
            indicator.setCurrentPage(0)
            return
        }

        val width = container.width
        if (width <= 0) {
            container.post { scrollToPage(page) }
            return
        }

        if (page == n && currentPage == n - 1) {
            val p0 = pages[0]
            p0.translationX = (n * width).toFloat()

            container.animate()
                .translationX((-(n) * width).toFloat())
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    p0.translationX = 0f
                    container.translationX = 0f
                    currentPage = 0
                    indicator.setCurrentPage(0)
                    onPageChanged()
                }.start()
            return
        } else if (page == -1 && currentPage == 0) {
            val pLast = pages[n - 1]
            pLast.translationX = (-n * width).toFloat()

            container.animate()
                .translationX(width.toFloat())
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    pLast.translationX = 0f
                    container.translationX = (-(n - 1) * width).toFloat()
                    currentPage = n - 1
                    indicator.setCurrentPage(n - 1)
                    onPageChanged()
                }.start()
            return
        }

        val targetPage = (page + n) % n
        currentPage = targetPage
        val targetX = currentPage * width
        container.animate()
            .translationX((-targetX).toFloat())
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                onPageChanged()
            }
            .start()
        indicator.setCurrentPage(currentPage)
    }

    fun getCurrentPage(): Int = currentPage
    fun getPageCount(): Int = pages.size
    fun getPageAt(index: Int): FrameLayout? = if (index in pages.indices) pages[index] else null
    fun getAllPages(): List<FrameLayout> = pages
}
