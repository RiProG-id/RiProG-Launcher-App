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

class FreeformController(
    private val activity: Activity,
    private val rootLayout: FrameLayout,
    private val preferences: SettingsManager,
    private val callback: InteractionCallback
) {
    private var currentTransformOverlay: TransformOverlay? = null
    private var transformingView: View? = null
    private var transformingViewOriginalParent: ViewGroup? = null
    private var transformingViewOriginalIndex = -1

    interface InteractionCallback {
        fun onSaveState()
        fun onRemoveItem(item: HomeItem?, view: View?)
        fun onShowAppInfo(item: HomeItem?)
    }

    fun showTransformOverlay(v: View) {
        if (currentTransformOverlay != null) return

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

        currentTransformOverlay = TransformOverlay(activity, v, preferences, object : TransformOverlay.OnSaveListener {
            override fun onMove(x: Float, y: Float) {}
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
            override fun onUninstall() {}
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
        })

        rootLayout.addView(
            currentTransformOverlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun saveTransform() {
        if (transformingView == null || rootLayout.width == 0) return
        val item = transformingView!!.tag as HomeItem? ?: return

        val cellWidth = rootLayout.width / HomeView.GRID_COLUMNS
        val density = activity.resources.displayMetrics.density
        val cellHeight = (rootLayout.height - (48 * density).toInt()) / HomeView.GRID_ROWS

        if (cellWidth > 0 && cellHeight > 0) {
            item.col = transformingView!!.x / cellWidth
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
                if (transformingViewOriginalParent != null) {
                    transformingViewOriginalParent!!.addView(transformingView, transformingViewOriginalIndex)
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
