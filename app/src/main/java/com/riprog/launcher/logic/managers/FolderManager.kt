package com.riprog.launcher.logic.managers

import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.AppItem

import android.annotation.SuppressLint
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView

class FolderManager(private val activity: MainActivity, private val settingsManager: SettingsManager) {
    private var currentFolderOverlay: View? = null

    @SuppressLint("ClickableViewAccessibility")
    fun openFolder(folderItem: HomeItem, folderView: View?, homeItems: MutableList<HomeItem>, allApps: List<AppItem>) {
        if (currentFolderOverlay != null) closeFolder()
        ThemeUtils.applyWindowBlur(activity.window, true)

        val container: FrameLayout = object : FrameLayout(activity) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }
        container.setBackgroundColor(0x33000000)
        container.setOnClickListener { closeFolder() }

        container.setOnTouchListener(object : View.OnTouchListener {
            var startY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                        v.performClick()
                    }
                    MotionEvent.ACTION_UP -> {
                        if (event.y - startY > dpToPx(100f)) {
                            closeFolder()
                            return true
                        }
                    }
                }
                return false
            }
        })

        val overlay = LinearLayout(activity)
        overlay.orientation = LinearLayout.VERTICAL
        overlay.background = ThemeUtils.getGlassDrawable(activity, settingsManager, 12f)
        overlay.setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(24f))
        overlay.elevation = dpToPx(16f).toFloat()
        overlay.gravity = Gravity.CENTER_HORIZONTAL
        overlay.setOnClickListener { }

        val adaptiveColor = ThemeUtils.getAdaptiveColor(activity, settingsManager, true)

        val titleText = TextView(activity)
        titleText.text = if (folderItem.folderName == null || folderItem.folderName!!.isEmpty()) "Folder" else folderItem.folderName
        titleText.setTextColor(adaptiveColor)
        titleText.textSize = 20f
        titleText.setTypeface(null, Typeface.BOLD)
        titleText.gravity = Gravity.CENTER
        titleText.setPadding(0, 0, 0, dpToPx(16f))
        overlay.addView(titleText)

        val titleEdit = EditText(activity)
        titleEdit.setText(folderItem.folderName)
        titleEdit.setTextColor(adaptiveColor)
        titleEdit.background = null
        titleEdit.gravity = Gravity.CENTER
        titleEdit.imeOptions = EditorInfo.IME_ACTION_DONE
        titleEdit.isSingleLine = true
        titleEdit.visibility = View.GONE
        titleEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                folderItem.folderName = s.toString()
                activity.saveHomeState()
            }
            override fun afterTextChanged(s: Editable) {}
        })
        overlay.addView(titleEdit)

        titleText.setOnClickListener {
            titleText.visibility = View.GONE
            titleEdit.visibility = View.VISIBLE
            titleEdit.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        titleEdit.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val newName = titleEdit.text.toString()
                folderItem.folderName = newName
                titleText.text = if (newName.isEmpty()) "Folder" else newName
                titleEdit.visibility = View.GONE
                titleText.visibility = View.VISIBLE
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                activity.saveHomeState()
                activity.homeView.refreshIcons(activity.model, activity.allApps)
                return@setOnEditorActionListener true
            }
            false
        }

        val grid = GridLayout(activity)
        grid.columnCount = 4
        grid.alignmentMode = GridLayout.ALIGN_MARGINS
        grid.useDefaultMargins = true

        val folderPadding = dpToPx(8f)
        if (folderItem.folderItems != null) {
            for (sub in folderItem.folderItems) {
                val subView = activity.createAppView(sub) ?: continue
                val glp = GridLayout.LayoutParams()
                glp.setMargins(folderPadding, folderPadding, folderPadding, folderPadding)
                subView.layoutParams = glp
                subView.tag = sub
                subView.setOnClickListener {
                    activity.handleAppLaunch(sub.packageName!!)
                    closeFolder()
                }
                subView.setOnLongClickListener {
                    closeFolder()
                    removeFromFolder(folderItem, sub, homeItems)
                    homeItems.add(sub)
                    sub.page = activity.homeView.currentPage
                    activity.homeView.addItemView(sub, subView)
                    activity.mainLayout.startExternalDrag(subView)
                    true
                }
                grid.addView(subView)
            }
        }
        overlay.addView(grid)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.setMargins(dpToPx(24f), 0, dpToPx(24f), 0)
        container.addView(overlay, lp)

        activity.mainLayout.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        currentFolderOverlay = container
    }

    fun closeFolder() {
        if (currentFolderOverlay != null) {
            activity.mainLayout.removeView(currentFolderOverlay)
            currentFolderOverlay = null
            ThemeUtils.applyWindowBlur(activity.window, false)
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(activity.mainLayout.windowToken, 0)
            activity.homeView.refreshIcons(activity.model, activity.allApps)
        }
    }

    fun mergeToFolder(target: HomeItem?, dragged: HomeItem?, homeItems: MutableList<HomeItem>) {
        if (target == null || dragged == null) return
        val targetPage = target.page
        homeItems.remove(dragged)
        homeItems.remove(target)

        val folder = HomeItem.createFolder("", target.col, target.row, targetPage)
        folder.folderItems.add(target)
        folder.folderItems.add(dragged)
        folder.rotation = 0f
        folder.scale = 1.0f
        folder.tiltX = 0f
        folder.tiltY = 0f
        homeItems.add(folder)

        activity.homeView.removeItemView(target)
        activity.homeView.removeItemView(dragged)
        activity.renderHomeItem(folder)
        activity.saveHomeState()
    }

    fun addToFolder(folder: HomeItem?, dragged: HomeItem?, homeItems: MutableList<HomeItem>) {
        if (folder == null || dragged == null) return
        homeItems.remove(dragged)
        folder.folderItems.add(dragged)

        activity.homeView.removeItemView(dragged)
        refreshFolderIconsOnHome(folder)
        activity.saveHomeState()
    }

    fun removeFromFolder(folder: HomeItem, item: HomeItem, homeItems: MutableList<HomeItem>) {
        val page = folder.page
        folder.folderItems.remove(item)
        if (folder.folderItems.size == 1) {
            val lastItem = folder.folderItems[0]
            homeItems.remove(folder)
            lastItem.col = folder.col
            lastItem.row = folder.row
            lastItem.page = page
            lastItem.rotation = folder.rotation
            lastItem.scale = folder.scale
            lastItem.tiltX = folder.tiltX
            lastItem.tiltY = folder.tiltY
            homeItems.add(lastItem)

            activity.homeView.removeItemView(folder)
            activity.renderHomeItem(lastItem)
        } else {
            refreshFolderIconsOnHome(folder)
        }
        activity.saveHomeState()
    }

    fun refreshFolderIconsOnHome(folder: HomeItem) {
        val pagesContainer = activity.homeView.getChildAt(0) as ViewGroup
        for (i in 0 until pagesContainer.childCount) {
            val page = pagesContainer.getChildAt(i) as ViewGroup
            for (j in 0 until page.childCount) {
                val v = page.getChildAt(j)
                if (v.tag === folder) {
                    val grid = findGridLayout(v as ViewGroup)
                    if (grid != null) activity.refreshFolderPreview(folder, grid)
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

    fun isFolderOpen(): Boolean {
        return currentFolderOverlay != null
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, activity.resources.displayMetrics
        ).toInt()
    }
}
