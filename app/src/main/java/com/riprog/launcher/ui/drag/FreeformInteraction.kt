package com.riprog.launcher.ui.drag

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.riprog.launcher.ui.drag.TransformOverlay
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import com.riprog.launcher.data.model.HomeItem

/**
 * Logic for managing the Freeform Edit Mode (Transform Overlay).
 */
class FreeformInteraction(
    private val activity: android.app.Activity,
    private val rootLayout: FrameLayout,
    private val preferences: LauncherPreferences,
    private val callback: InteractionCallback
) {
    interface InteractionCallback {
        fun onSaveState()
        fun onRemoveItem(item: HomeItem, view: View)
        fun onShowAppInfo(item: HomeItem)
        fun onEdgeScroll(x: Float)
        fun onDragStart(x: Float, page: Int)
        fun onCollision(otherView: View)
        fun findItemAt(x: Float, y: Float, exclude: View): View?
        fun onUninstallItem(item: HomeItem)
        fun onUpdateHomeItemFromTransform(v: View)
        fun onUpdateViewPosition(item: HomeItem, v: View)
    }

    private var currentTransformOverlay: TransformOverlay? = null
    private var transformingView: View? = null
    private var transformingViewOriginalParent: ViewGroup? = null
    private var transformingViewOriginalIndex: Int = -1

    /**
     * Enters Freeform Edit mode for the specified view.
     */
    fun showTransformOverlay(v: View) {
        if (currentTransformOverlay != null) return

        transformingView = v
        transformingViewOriginalParent = v.parent as? ViewGroup
        transformingViewOriginalIndex = transformingViewOriginalParent?.indexOfChild(v) ?: -1

        // Calculate absolute position
        var x = v.x
        var y = v.y
        var p = v.parent
        while (p != null && p !== rootLayout) {
            if (p is View) {
                x += p.x
                y += p.y
            }
            p = p.parent
        }

        transformingViewOriginalParent?.removeView(v)
        rootLayout.addView(v)
        v.x = x
        v.y = y

        currentTransformOverlay = TransformOverlay(activity, v, preferences, object : TransformOverlay.OnSaveListener {
            override fun onMove(x: Float, y: Float) {
                callback.onEdgeScroll(x)
            }
            override fun onMoveStart(x: Float, y: Float) {
                val item = v.tag as? HomeItem
                callback.onDragStart(x, item?.page ?: -1)
            }
            override fun onSave() {
                callback.onUpdateHomeItemFromTransform(v)
                callback.onSaveState()
                closeTransformOverlay()
            }
            override fun onCancel() {
                closeTransformOverlay()
            }
            override fun onRemove() {
                val item = v.tag as HomeItem
                callback.onRemoveItem(item, v)
                transformingView = null
                closeTransformOverlay()
            }
            override fun onAppInfo() {
                callback.onShowAppInfo(v.tag as HomeItem)
            }
            override fun onUninstall() {
                callback.onUninstallItem(v.tag as HomeItem)
            }
            override fun onCollision(otherView: View) {
                callback.onUpdateHomeItemFromTransform(v)
                callback.onSaveState()
                closeTransformOverlay()
                showTransformOverlay(otherView)
            }
            override fun findItemAt(x: Float, y: Float, exclude: View): View? {
                return callback.findItemAt(x, y, exclude)
            }
        })
        rootLayout.addView(currentTransformOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    fun closeTransformOverlay() {
        currentTransformOverlay?.let {
            rootLayout.removeView(it)
            currentTransformOverlay = null

            if (transformingView != null && transformingViewOriginalParent != null) {
                rootLayout.removeView(transformingView)
                transformingViewOriginalParent?.addView(transformingView, transformingViewOriginalIndex)
                val item = transformingView?.tag as? HomeItem
                if (item != null) {
                    callback.onUpdateViewPosition(item, transformingView!!)
                }
            }
            transformingView = null
            transformingViewOriginalParent = null
        }
    }

    fun startDirectMove(x: Float, y: Float) {
        currentTransformOverlay?.startDirectMove(x, y)
    }

    fun isTransforming(): Boolean = currentTransformOverlay != null
    fun getOverlay(): View? = currentTransformOverlay
}
