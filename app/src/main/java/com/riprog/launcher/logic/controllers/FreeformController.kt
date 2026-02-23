package com.riprog.launcher.logic.controllers

import com.riprog.launcher.ui.views.layout.TransformOverlay
import com.riprog.launcher.ui.views.layout.MainLayout
import com.riprog.launcher.ui.views.home.HomeView
import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.model.HomeItem

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FreeformController(
    private val activity: Activity,
    private val rootLayout: FrameLayout,
    private val preferences: SettingsManager,
    private val callback: InteractionCallback
) {
    var currentTransformOverlay: TransformOverlay? = null
        private set
    private var transformingView: View? = null
    private var transformingViewOriginalParent: ViewGroup? = null
    private var transformingViewOriginalIndex = -1

    interface InteractionCallback {
        fun onSaveState()
        fun onRemoveItem(item: HomeItem?, view: View?)
        fun onShowAppInfo(item: HomeItem?)
    }

    fun showTransformOverlay(v: View, initialTouchX: Float = -1f, initialTouchY: Float = -1f) {
        if (currentTransformOverlay != null) {
            if (transformingView === v) return
            closeTransformOverlay()
        }

        transformingView = v
        transformingViewOriginalParent = v.parent as? ViewGroup
        if (transformingViewOriginalParent != null) {
            transformingViewOriginalIndex = transformingViewOriginalParent!!.indexOfChild(v)
        }

        var x = v.x
        var y = v.y
        var p = v.parent as? View
        while (p != null && p !== rootLayout) {
            x += p.x
            y += p.y
            p = p.parent as? View
        }

        if (transformingViewOriginalParent != null) {
            transformingViewOriginalParent!!.removeView(v)
        }
        rootLayout.addView(v)
        v.x = x
        v.y = y

        val homeView = (activity as? MainActivity)?.homeView
        currentTransformOverlay = TransformOverlay(activity, v, preferences, object : TransformOverlay.OnSaveListener {
            override fun onMove(x: Float, y: Float) {
                homeView?.checkEdgeScroll(x)
                homeView?.pageIndicator?.setCurrentPage(homeView.resolvePageIndex(x))
            }

            override fun onMoveStart(x: Float, y: Float) {}

            override fun onSave() {
                saveTransform()
                closeTransformOverlay()
            }
            override fun onCancel() {
                closeTransformOverlay()
            }
            override fun onRemove() {
                val item = v.tag as HomeItem?
                callback.onRemoveItem(item, v)
                transformingView = null
                closeTransformOverlay()
            }
            override fun onAppInfo() {
                callback.onShowAppInfo(v.tag as HomeItem?)
            }
            override fun onCollision(otherView: View) {
                if (handleFolderDrop(v, otherView)) {
                    closeTransformOverlay()
                } else {
                    saveTransform()
                    closeTransformOverlay()
                    showTransformOverlay(otherView)
                }
            }
            override fun findItemAt(x: Float, y: Float, exclude: View): View? {
                val mainLayout = rootLayout as? MainLayout ?: return null
                return mainLayout.findTouchedHomeItem(x, y, exclude)
            }

            override fun onSnapToGrid(v: View): Boolean {
                return snapToGrid(v)
            }
        })

        if (initialTouchX != -1f && initialTouchY != -1f) {
            val cellWidth = homeView?.getCellWidth() ?: 0f
            val cellHeight = homeView?.getCellHeight() ?: 0f
            val item = v.tag as? HomeItem

            val w = if (v.width > 0) v.width.toFloat() else if (item != null) cellWidth * item.spanX else 0f
            val h = if (v.height > 0) v.height.toFloat() else if (item != null) cellHeight * item.spanY else 0f

            // Immediately set pivot to center
            v.pivotX = w / 2f
            v.pivotY = h / 2f

            v.x = initialTouchX - w / 2f
            v.y = initialTouchY - h / 2f

            currentTransformOverlay?.startImmediateDrag(initialTouchX, initialTouchY)
        }

        rootLayout.addView(
            currentTransformOverlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun snapToGrid(v: View): Boolean {
        val mainActivity = activity as? MainActivity ?: return false
        val homeView = mainActivity.homeView
        val item = v.tag as? HomeItem ?: return false

        homeView.stopEdgeScroll()

        val cellWidth = homeView.getCellWidth()
        val cellHeight = homeView.getCellHeight()
        if (cellWidth <= 0 || cellHeight <= 0) return false

        val density = activity.resources.displayMetrics.density
        val horizontalPadding = HomeView.HORIZONTAL_PADDING_DP * density

        // Find center of the view to determine target position
        val midX = v.x + v.width / 2f
        val midY = v.y + v.height / 2f

        val otherView = (rootLayout as? MainLayout)?.findTouchedHomeItem(midX, midY, v)
        if (otherView != null) {
            val hitBufferX = otherView.width * 0.25f
            val hitBufferY = otherView.height * 0.25f

            // Check if drop is within the center area of the target to prevent accidental merges
            // and we need to translate midX/midY to otherView's coordinate system
            // findTouchedHomeItem already confirmed it's within bounds, now we check inner bounds

            // Need absolute coordinates for otherView
            var ox = otherView.x
            var oy = otherView.y
            var op = otherView.parent as? View
            while (op != null && op !== rootLayout) {
                ox += op.x
                oy += op.y
                op = op.parent as? View
            }

            if (midX >= ox + hitBufferX && midX <= ox + otherView.width - hitBufferX &&
                midY >= oy + hitBufferY && midY <= oy + otherView.height - hitBufferY) {
                if (handleFolderDrop(v, otherView)) {
                    closeTransformOverlay()
                    return true
                }
            }
        }

        if (!preferences.isFreeformHome) {
            val targetPage = homeView.resolvePageIndex(v.x + v.width / 2f)
            val relativeX = v.x - (homeView.pages[targetPage].left + homeView.pagesContainer.translationX)

            val newCol = max(0, min(preferences.columns - item.spanX, ((relativeX - horizontalPadding) / cellWidth).roundToInt()))
            val newRow = max(0, min(HomeView.GRID_ROWS - item.spanY, (v.y / cellHeight).roundToInt()))

            item.col = newCol.toFloat()
            item.row = newRow.toFloat()
            item.page = targetPage
            item.rotation = 0f
            item.scale = 1.0f
            item.tiltX = 0f
            item.tiltY = 0f

            // Update page indicator to reflect target
            homeView.pageIndicator.setCurrentPage(targetPage)

            // Animate to snapped position relative to current display (MainLayout coordinates)
            val snappedX = newCol * cellWidth + horizontalPadding + (homeView.pages[targetPage].left + homeView.pagesContainer.translationX)
            val snappedY = newRow * cellHeight

            v.animate()
                .x(snappedX)
                .y(snappedY)
                .rotation(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()
        }
        mainActivity.saveHomeState()
        return false
    }

    private fun saveTransform() {
        if (transformingView == null || rootLayout.width == 0) return
        val item = transformingView!!.tag as HomeItem? ?: return

        val homeView = (activity as? MainActivity)?.homeView ?: return
        val cellWidth = homeView.getCellWidth()
        val cellHeight = homeView.getCellHeight()

        val absX = transformingView!!.x
        val targetPage = homeView.resolvePageIndex(absX + transformingView!!.width / 2f)
        item.page = targetPage

        if (cellWidth > 0 && cellHeight > 0) {
            val density = activity.resources.displayMetrics.density
            val horizontalPadding = HomeView.HORIZONTAL_PADDING_DP * density
            val relativeX = absX - (homeView.pages[targetPage].left + homeView.pagesContainer.translationX)
            item.col = (relativeX - horizontalPadding) / cellWidth
            item.row = transformingView!!.y / cellHeight
        }

        item.rotation = transformingView!!.rotation
        item.scale = transformingView!!.scaleX
        item.tiltX = transformingView!!.rotationX
        item.tiltY = transformingView!!.rotationY
        callback.onSaveState()
    }

    private fun handleFolderDrop(draggedView: View, targetView: View): Boolean {
        val draggedItem = draggedView.tag as? HomeItem ?: return false
        val targetItem = targetView.tag as? HomeItem ?: return false
        val mainActivity = activity as? MainActivity ?: return false

        if (draggedItem.type == HomeItem.Type.APP) {
            if (targetItem.type == HomeItem.Type.APP) {
                mainActivity.folderManager.mergeToFolder(targetItem, draggedItem, mainActivity.homeItems)
                transformingViewOriginalParent = null
                return true
            } else if (targetItem.type == HomeItem.Type.FOLDER) {
                mainActivity.folderManager.addToFolder(targetItem, draggedItem, mainActivity.homeItems)
                transformingViewOriginalParent = null
                return true
            }
        }
        return false
    }

    fun closeTransformOverlay() {
        if (currentTransformOverlay != null) {
            rootLayout.removeView(currentTransformOverlay)
            currentTransformOverlay = null

            if (transformingView != null) {
                rootLayout.removeView(transformingView)

                val item = transformingView!!.tag as? HomeItem
                if (item != null && activity is MainActivity) {
                    activity.renderHomeItem(item)
                    activity.saveHomeState()
                }
            }
            transformingView = null
            transformingViewOriginalParent = null
        }
    }

    fun isTransforming(): Boolean {
        return currentTransformOverlay != null
    }
}
