package com.riprog.launcher.ui.home

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.riprog.launcher.R
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.utils.SettingsManager

class GridManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        const val GRID_COLUMNS = 4
        const val GRID_ROWS = 6
    }

    fun updateViewPosition(item: HomeItem, view: View, containerWidth: Int, containerHeight: Int, paddingLeft: Int, paddingTop: Int, paddingRight: Int, paddingBottom: Int) {
        val availW = containerWidth - paddingLeft - paddingRight
        val availH = containerHeight - paddingTop - paddingBottom

        val cellWidth = if (availW > 0) availW / GRID_COLUMNS else 0
        val cellHeight = if (availH > 0) availH / GRID_ROWS else 0

        if (cellWidth <= 0 || cellHeight <= 0) {
            view.visibility = View.INVISIBLE
            return
        }
        view.visibility = View.VISIBLE

        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
        if (item.type == HomeItem.Type.WIDGET || (item.type == HomeItem.Type.FOLDER && (item.spanX > 1.0f || item.spanY > 1.0f))) {
            lp.width = (cellWidth * item.spanX).toInt()
            lp.height = (cellHeight * item.spanY).toInt()
        } else {
            val size = context.resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            lp.width = size * 2
            lp.height = size * 2
        }
        view.layoutParams = lp

        if (item.type == HomeItem.Type.APP || (item.type == HomeItem.Type.FOLDER && item.spanX <= 1.0f && item.spanY <= 1.0f)) {
            val offsetX = if (settingsManager.isFreeformHome) 0f else (cellWidth - lp.width) / 2f
            val offsetY = if (settingsManager.isFreeformHome) 0f else (cellHeight - lp.height) / 2f
            view.x = item.col * cellWidth + offsetX
            view.y = item.row * cellHeight + offsetY
        } else {
            view.x = item.col * cellWidth
            view.y = item.row * cellHeight
        }

        view.rotation = item.rotation
        view.scaleX = item.scaleX
        view.scaleY = item.scaleY
        view.rotationX = item.tiltX
        view.rotationY = item.tiltY
    }

    fun isAreaOccupied(col: Int, row: Int, spanX: Int, spanY: Int, page: Int, exclude: HomeItem?, homeItems: List<HomeItem>): Boolean {
        for (item in homeItems) {
            if (item === exclude || item.page != page) continue
            val itemCol = Math.round(item.col)
            val itemRow = Math.round(item.row)
            val itemSpanX = Math.round(item.spanX)
            val itemSpanY = Math.round(item.spanY)
            if (col < itemCol + itemSpanX && col + spanX > itemCol &&
                row < itemRow + itemSpanY && row + spanY > itemRow) {
                return true
            }
        }
        return false
    }

    fun findNearestEmptySpot(item: HomeItem, homeItems: List<HomeItem>): Pair<Int, Int>? {
        val spanX = Math.round(item.spanX)
        val spanY = Math.round(item.spanY)
        for (r in 0 until GRID_ROWS - spanY + 1) {
            for (c in 0 until GRID_COLUMNS - spanX + 1) {
                if (!isAreaOccupied(c, r, spanX, spanY, item.page, item, homeItems)) {
                    return Pair(c, r)
                }
            }
        }
        return null
    }

    fun findCollision(draggedView: View, currentPageLayout: ViewGroup): HomeItem? {
        val centerX = draggedView.x + draggedView.width / 2f
        val centerY = draggedView.y + draggedView.height / 2f

        for (i in 0 until currentPageLayout.childCount) {
            val child = currentPageLayout.getChildAt(i)
            if (child === draggedView) continue

            val targetItem = child.tag as? HomeItem ?: continue

            if (centerX >= child.x && centerX <= child.x + child.width &&
                centerY >= child.y && centerY <= child.y + child.height
            ) {
                return targetItem
            }
        }
        return null
    }
}
