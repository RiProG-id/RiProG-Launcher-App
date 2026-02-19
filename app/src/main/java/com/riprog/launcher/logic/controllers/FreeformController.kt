package com.riprog.launcher.logic.controllers

import com.riprog.launcher.ui.views.layout.TransformOverlay
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.LauncherSettings

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class FreeformController(
    private val activity: Activity,
    private val rootLayout: FrameLayout,
    private var settings: LauncherSettings,
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

    fun updateSettings(newSettings: LauncherSettings) {
        this.settings = newSettings
        currentTransformOverlay?.updateSettings(newSettings)
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

        currentTransformOverlay = TransformOverlay(activity, v, settings, object : TransformOverlay.OnSaveListener {
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
                saveTransform()
                closeTransformOverlay()
                showTransformOverlay(otherView)
            }
            override fun findItemAt(x: Float, y: Float, exclude: View): View? {
                return null
            }
        })

        rootLayout.addView(
            currentTransformOverlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun saveTransform() {
        if (transformingView == null) return
        val item = transformingView!!.tag as HomeItem? ?: return
        item.rotation = transformingView!!.rotation
        item.scale = transformingView!!.scaleX
        item.tiltX = transformingView!!.rotationX
        item.tiltY = transformingView!!.rotationY
        callback.onSaveState()
    }

    fun closeTransformOverlay() {
        if (currentTransformOverlay != null) {
            rootLayout.removeView(currentTransformOverlay)
            currentTransformOverlay = null

            if (transformingView != null && transformingViewOriginalParent != null) {
                rootLayout.removeView(transformingView)
                transformingViewOriginalParent!!.addView(transformingView, transformingViewOriginalIndex)
            }
            transformingView = null
            transformingViewOriginalParent = null
        }
    }

    fun isTransforming(): Boolean {
        return currentTransformOverlay != null
    }
}
