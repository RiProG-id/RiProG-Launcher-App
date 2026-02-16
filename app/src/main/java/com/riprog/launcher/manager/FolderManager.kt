package com.riprog.launcher.manager

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.riprog.launcher.MainActivity
import com.riprog.launcher.model.AppItem
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.model.LauncherModel
import com.riprog.launcher.utils.SettingsManager
import com.riprog.launcher.utils.ThemeUtils

class FolderManager(
    private val activity: MainActivity,
    private val settingsManager: SettingsManager
) {
    private var currentFolderOverlay: View? = null

    fun openFolder(folderItem: HomeItem, folderView: View, homeItems: MutableList<HomeItem>, allApps: List<AppItem>, model: LauncherModel?) {
        if (currentFolderOverlay != null) closeFolder()
        activity.setOverlayBlur(true)

        val container = FrameLayout(activity).apply {
            setBackgroundColor(0x33000000)
            setOnClickListener { closeFolder() }
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            var startY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startY = event.y
                    MotionEvent.ACTION_UP -> if (event.y - startY > dpToPx(100)) {
                        closeFolder()
                        return true
                    }
                }
                return false
            }
        })

        val overlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeUtils.getGlassDrawable(activity, settingsManager, 12f)
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            elevation = dpToPx(16).toFloat()
            gravity = Gravity.CENTER_HORIZONTAL
            setOnClickListener { }
        }

        val titleText = TextView(activity).apply {
            text = if (folderItem.folderName.isNullOrEmpty()) "Folder" else folderItem.folderName
            setTextColor(ThemeUtils.getAdaptiveColor(activity, settingsManager, true))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16))
        }
        overlay.addView(titleText)

        val titleEdit = EditText(activity).apply {
            setText(folderItem.folderName)
            setTextColor(ThemeUtils.getAdaptiveColor(activity, settingsManager, true))
            background = null
            gravity = Gravity.CENTER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    folderItem.folderName = s.toString()
                    activity.saveHomeState()
                }
                override fun afterTextChanged(s: Editable) {}
            })
        }
        overlay.addView(titleEdit)

        titleText.setOnClickListener {
            titleText.visibility = View.GONE
            titleEdit.visibility = View.VISIBLE
            titleEdit.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        titleEdit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val newName = titleEdit.text.toString()
                folderItem.folderName = newName
                titleText.text = if (newName.isEmpty()) "Folder" else newName
                titleEdit.visibility = View.GONE
                titleText.visibility = View.VISIBLE
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                activity.saveHomeState()
                activity.getHomeView()?.refreshIcons(model!!, allApps)
                true
            } else false
        }

        val grid = GridLayout(activity).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = true
        }

        val folderPadding = dpToPx(8)
        folderItem.folderItems?.let { items ->
            for (sub in items) {
                val subView = activity.itemRenderer.createAppView(sub, true, activity.getModel(), activity.allApps)
                subView.layoutParams = GridLayout.LayoutParams().apply {
                    setMargins(folderPadding, folderPadding, folderPadding, folderPadding)
                }
                subView.tag = sub
                subView.setOnClickListener {
                    activity.handleAppLaunch(sub.packageName)
                    closeFolder()
                }
                subView.setOnLongClickListener {
                    closeFolder()
                    removeFromFolder(folderItem, sub, homeItems)
                    homeItems.add(sub)
                    sub.page = activity.getHomeView()?.getCurrentPage() ?: 0
                    activity.getHomeView()?.addItemView(sub, subView)
                    activity.mainLayout?.startExternalDrag(subView)
                    true
                }
                grid.addView(subView)
            }
        }
        overlay.addView(grid)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.setMargins(dpToPx(24), 0, dpToPx(24), 0)
        container.addView(overlay, lp)

        activity.mainLayout?.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        currentFolderOverlay = container
    }

    fun closeFolder() {
        currentFolderOverlay?.let {
            activity.mainLayout?.removeView(it)
            currentFolderOverlay = null
            activity.setOverlayBlur(false)
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(activity.mainLayout?.windowToken, 0)
            activity.getHomeView()?.refreshIcons(activity.getModel()!!, activity.allApps)
        }
    }

    fun mergeToFolder(target: HomeItem, dragged: HomeItem, homeItems: MutableList<HomeItem>) {
        val oldDraggedPage = dragged.page
        val targetPage = target.page

        homeItems.remove(dragged)
        homeItems.remove(target)

        val folder = HomeItem.createFolder("", target.col, target.row, targetPage)
        folder.folderItems?.add(target)
        folder.folderItems?.add(dragged)
        folder.rotation = 0f
        folder.scaleX = 1.0f
        folder.scaleY = 1.0f
        folder.tiltX = 0f
        folder.tiltY = 0f
        homeItems.add(folder)

        activity.getHomeView()?.removeItemsByPackage(target.packageName)
        activity.getHomeView()?.removeItemsByPackage(dragged.packageName)
        activity.renderHomeItem(folder)

        if (oldDraggedPage != targetPage) activity.savePage(oldDraggedPage)
        activity.savePage(targetPage)
    }

    fun addToFolder(folder: HomeItem, dragged: HomeItem, homeItems: MutableList<HomeItem>) {
        val oldDraggedPage = dragged.page
        val targetPage = folder.page

        homeItems.remove(dragged)
        folder.folderItems?.add(dragged)

        activity.getHomeView()?.removeItemsByPackage(dragged.packageName)
        refreshFolderIconsOnHome(folder)

        if (oldDraggedPage != targetPage) activity.savePage(oldDraggedPage)
        activity.savePage(targetPage)
    }

    fun removeFromFolder(folder: HomeItem, item: HomeItem, homeItems: MutableList<HomeItem>) {
        val page = folder.page
        folder.folderItems?.remove(item)
        if (folder.folderItems?.size == 1) {
            val lastItem = folder.folderItems!![0]
            homeItems.remove(folder)
            lastItem.col = folder.col
            lastItem.row = folder.row
            lastItem.page = page
            lastItem.rotation = folder.rotation
            lastItem.scaleX = folder.scaleX
            lastItem.scaleY = folder.scaleY
            lastItem.tiltX = folder.tiltX
            lastItem.tiltY = folder.tiltY
            homeItems.add(lastItem)

            removeFolderView(folder)
            activity.renderHomeItem(lastItem)
        } else {
            refreshFolderIconsOnHome(folder)
        }
        activity.savePage(page)
    }

    private fun removeFolderView(folder: HomeItem) {
        val pagesContainer = activity.getHomeView()?.pagesContainer ?: return
        for (i in 0 until pagesContainer.childCount) {
            val page = pagesContainer.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until page.childCount) {
                val v = page.getChildAt(j)
                if (v.tag === folder) {
                    page.removeView(v)
                    return
                }
            }
        }
    }

    fun refreshFolderIconsOnHome(folder: HomeItem) {
        val pagesContainer = activity.getHomeView()?.pagesContainer ?: return
        for (i in 0 until pagesContainer.childCount) {
            val page = pagesContainer.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until page.childCount) {
                val v = page.getChildAt(j)
                if (v.tag === folder) {
                    val grid = findGridLayout(v as ViewGroup)
                    if (grid != null) {
                        val hv = activity.getHomeView()
                        activity.itemRenderer.refreshFolderPreview(folder, grid, activity.getModel(), activity.allApps, hv?.width ?: 0, hv?.height ?: 0)
                    }
                    return
                }
            }
        }
    }

    private fun findGridLayout(container: ViewGroup): GridLayout? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is GridLayout) return child
            if (child is ViewGroup) {
                val g = findGridLayout(child)
                if (g != null) return g
            }
        }
        return null
    }

    fun isFolderOpen(): Boolean = currentFolderOverlay != null

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), activity.resources.displayMetrics
        ).toInt()
    }
}
