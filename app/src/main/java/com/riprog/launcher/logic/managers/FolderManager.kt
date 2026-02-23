package com.riprog.launcher.logic.managers

import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.AppItem

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewTreeObserver
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
    private var isProcessingDrop = false
    private var isReordering = false

    @SuppressLint("ClickableViewAccessibility")
    fun openFolder(folderItem: HomeItem, folderView: View?, homeItems: MutableList<HomeItem>, allApps: List<AppItem>) {
        val wasOpen = currentFolderOverlay != null
        if (wasOpen) {
            val oldOverlay = currentFolderOverlay!!
            currentFolderOverlay = null
            activity.mainLayout.removeView(oldOverlay)
        } else {
            ThemeUtils.applyWindowBlur(activity.window, true)
        }

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
        overlay.isClickable = true
        overlay.isFocusable = true
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

        val finishRename = {
            val newName = titleEdit.text.toString()
            folderItem.folderName = newName
            titleText.text = if (newName.isEmpty()) "Folder" else newName
            titleEdit.visibility = View.GONE
            titleText.visibility = View.VISIBLE
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(titleEdit.windowToken, 0)
            activity.saveHomeState()
            activity.homeView.refreshIcons(activity.model, activity.allApps)
        }

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
            titleEdit.setSelection(titleEdit.text.length)
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        titleEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishRename()
                return@setOnEditorActionListener true
            }
            false
        }

        overlay.setOnClickListener {
            if (titleEdit.visibility == View.VISIBLE) {
                finishRename()
            }
        }

        val grid = GridLayout(activity)
        grid.columnCount = 4
        grid.alignmentMode = GridLayout.ALIGN_MARGINS
        grid.useDefaultMargins = true

        val folderPadding = dpToPx(8f)
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
                val data = ClipData.newPlainText("index", grid.indexOfChild(subView).toString())
                val shadow = View.DragShadowBuilder(subView)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    subView.startDragAndDrop(data, shadow, subView, 0)
                } else {
                    @Suppress("DEPRECATION")
                    subView.startDrag(data, shadow, subView, 0)
                }
                subView.visibility = View.INVISIBLE
                true
            }
            grid.addView(subView)
        }
        overlay.addView(grid)

        val dragListener = View.OnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    isProcessingDrop = false
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val draggedView = event.localState as? View
                    if (draggedView != null) {
                        val gridLocation = IntArray(2)
                        grid.getLocationInWindow(gridLocation)
                        val containerLocation = IntArray(2)
                        container.getLocationInWindow(containerLocation)

                        val xInWindow = event.x + containerLocation[0]
                        val yInWindow = event.y + containerLocation[1]

                        val relativeX = xInWindow - gridLocation[0]
                        val relativeY = yInWindow - gridLocation[1]

                        val targetIndex = calculateTargetIndex(grid, relativeX, relativeY)
                        if (targetIndex != -1 && targetIndex != grid.indexOfChild(draggedView)) {
                            reorderGridVisually(grid, draggedView, targetIndex)
                        }
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    if (isProcessingDrop) return@OnDragListener false
                    isProcessingDrop = true

                    val draggedView = event.localState as? View
                    val draggedItem = draggedView?.tag as? HomeItem
                    if (draggedItem != null) {
                        val x = event.x
                        val y = event.y

                        val gridLocation = IntArray(2)
                        grid.getLocationInWindow(gridLocation)
                        val containerLocation = IntArray(2)
                        container.getLocationInWindow(containerLocation)

                        val dropXInWindow = x + containerLocation[0]
                        val dropYInWindow = y + containerLocation[1]

                        val overlayLocation = IntArray(2)
                        overlay.getLocationInWindow(overlayLocation)

                        if (dropXInWindow >= overlayLocation[0] && dropXInWindow <= overlayLocation[0] + overlay.width &&
                            dropYInWindow >= overlayLocation[1] && dropYInWindow <= overlayLocation[1] + overlay.height) {

                            val finalIndexInGrid = grid.indexOfChild(draggedView)
                            val currentIndexInList = folderItem.folderItems.indexOf(draggedItem)

                            if (currentIndexInList != -1 && finalIndexInGrid != currentIndexInList) {
                                folderItem.folderItems.removeAt(currentIndexInList)
                                val finalTarget = if (finalIndexInGrid > folderItem.folderItems.size) folderItem.folderItems.size else finalIndexInGrid
                                folderItem.folderItems.add(finalTarget, draggedItem)
                                activity.saveHomeState()
                            }
                            draggedView.visibility = View.VISIBLE
                        } else {
                            // Dropped outside folder bounds -> Remove from folder
                            closeFolder()
                            removeFromFolder(folderItem, draggedItem, homeItems)

                            val targetPage = activity.homeView.currentPage
                            draggedItem.page = targetPage

                            // Calculate dropped position in HomeView's current page coordinates
                            val homeLocation = IntArray(2)
                            activity.homeView.getLocationInWindow(homeLocation)

                            val pagesContainer = activity.homeView.pagesContainer
                            val currentPageLayout = activity.homeView.pages[targetPage]

                            val dropXOnHome = dropXInWindow - homeLocation[0]
                            val dropYOnHome = dropYInWindow - homeLocation[1]

                            val relativeX = dropXOnHome - (pagesContainer.translationX + currentPageLayout.left)
                            val relativeY = dropYOnHome - (pagesContainer.translationY + currentPageLayout.top)

                            // Explicitly remove from parent before adding to HomeView
                            (draggedView.parent as? ViewGroup)?.removeView(draggedView)
                            activity.homeView.addItemView(draggedItem, draggedView)
                            draggedView.x = relativeX - draggedView.width / 2f
                            draggedView.y = relativeY - draggedView.height / 2f
                            draggedView.visibility = View.VISIBLE

                            activity.homeView.snapToGrid(draggedItem, draggedView)
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    val draggedView = event.localState as? View
                    draggedView?.visibility = View.VISIBLE
                    true
                }
                else -> true
            }
        }
        container.setOnDragListener(dragListener)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.setMargins(dpToPx(24f), 0, dpToPx(24f), 0)
        container.addView(overlay, lp)

        activity.mainLayout.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        currentFolderOverlay = container
    }

    private fun reorderGridVisually(grid: GridLayout, draggedView: View, targetIndex: Int) {
        if (isReordering) return
        val currentIndex = grid.indexOfChild(draggedView)
        if (currentIndex == -1 || targetIndex == -1 || currentIndex == targetIndex) return
        if (targetIndex >= grid.childCount) return

        isReordering = true
        val positions = mutableMapOf<View, Pair<Float, Float>>()
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            positions[child] = Pair(child.x, child.y)
        }

        grid.removeView(draggedView)
        grid.addView(draggedView, targetIndex)

        grid.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                grid.viewTreeObserver.removeOnPreDrawListener(this)
                for (i in 0 until grid.childCount) {
                    val child = grid.getChildAt(i)
                    if (child === draggedView) continue
                    val oldPos = positions[child]
                    if (oldPos != null) {
                        val newX = child.x
                        val newY = child.y
                        if (newX != oldPos.first || newY != oldPos.second) {
                            child.x = oldPos.first
                            child.y = oldPos.second
                            child.animate().x(newX).y(newY).setDuration(150).start()
                        }
                    }
                }
                isReordering = false
                return true
            }
        })
    }

    private fun calculateTargetIndex(grid: GridLayout, x: Float, y: Float): Int {
        if (grid.childCount == 0) return 0

        var closestIndex = -1
        var minDistance = Double.MAX_VALUE

        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            // We should use the slots. Since it's a 4-column grid:
            val centerX = child.x + child.width / 2f
            val centerY = child.y + child.height / 2f

            val dist = Math.sqrt(Math.pow((x - centerX).toDouble(), 2.0) + Math.pow((y - centerY).toDouble(), 2.0))
            if (dist < minDistance) {
                minDistance = dist
                closestIndex = i
            }
        }
        return closestIndex
    }

    fun closeFolder() {
        if (currentFolderOverlay != null) {
            val overlay = currentFolderOverlay!!
            currentFolderOverlay = null
            activity.mainLayout.removeView(overlay)
            ThemeUtils.applyWindowBlur(activity.window, false)
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(activity.mainLayout.windowToken, 0)
            activity.homeView.refreshIcons(activity.model, activity.allApps)
        }
    }

    fun mergeToFolder(target: HomeItem?, dragged: HomeItem?, homeItems: MutableList<HomeItem>) {
        if (target == null || dragged == null || target === dragged) return
        val backupHomeItems = ArrayList(homeItems)
        val targetPage = target.page
        var createdFolder: HomeItem? = null

        try {
            val folder = HomeItem.createFolder("", target.col, target.row, targetPage)
            createdFolder = folder
            folder.folderItems.add(target)
            folder.folderItems.add(dragged)
            folder.rotation = 0f
            folder.scale = 1.0f
            folder.tiltX = 0f
            folder.tiltY = 0f

            if (folder.folderItems.size != 2) throw IllegalStateException("Invalid folder state")

            // Atomic removal using iterator to be 100% sure
            val it = homeItems.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if (item === dragged || item === target) {
                    it.remove()
                }
            }
            homeItems.add(folder)

            activity.homeView.removeItemView(target)
            activity.homeView.removeItemView(dragged)
            if (activity.renderHomeItem(folder) == null) throw IllegalStateException("Failed to render folder")

            activity.saveHomeState()
        } catch (e: Exception) {
            homeItems.clear()
            homeItems.addAll(backupHomeItems)
            if (createdFolder != null) activity.homeView.removeItemView(createdFolder)
            activity.renderHomeItem(target)
            activity.renderHomeItem(dragged)
            activity.homeView.refreshIcons(activity.model, activity.allApps)
        }
    }

    fun addToFolder(folder: HomeItem?, dragged: HomeItem?, homeItems: MutableList<HomeItem>) {
        if (folder == null || dragged == null || folder === dragged) return
        if (folder.folderItems.any { it === dragged }) return

        val backupHomeItems = ArrayList(homeItems)
        val backupFolderItems = ArrayList(folder.folderItems)

        try {
            folder.folderItems.add(dragged)

            // Atomic removal
            val it = homeItems.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if (item === dragged) {
                    it.remove()
                }
            }

            activity.homeView.removeItemView(dragged)
            refreshFolderIconsOnHome(folder)
            activity.saveHomeState()
        } catch (e: Exception) {
            folder.folderItems.clear()
            folder.folderItems.addAll(backupFolderItems)
            homeItems.clear()
            homeItems.addAll(backupHomeItems)
            activity.renderHomeItem(dragged)
            refreshFolderIconsOnHome(folder)
        }
    }

    fun removeFromFolder(folder: HomeItem, item: HomeItem, homeItems: MutableList<HomeItem>) {
        val backupHomeItems = ArrayList(homeItems)
        val backupFolderItems = ArrayList(folder.folderItems)
        val page = folder.page

        try {
            folder.folderItems.removeAll { it === item }

            if (folder.folderItems.size <= 1) {
                if (folder.folderItems.size == 1) {
                    val lastItem = folder.folderItems[0]
                    homeItems.removeAll { it === folder }

                    lastItem.col = folder.col
                    lastItem.row = folder.row
                    lastItem.page = page
                    lastItem.rotation = folder.rotation
                    lastItem.scale = folder.scale
                    lastItem.tiltX = folder.tiltX
                    lastItem.tiltY = folder.tiltY

                    if (!homeItems.any { it === lastItem }) {
                        homeItems.add(lastItem)
                    }

                    activity.homeView.removeItemView(folder)
                    activity.renderHomeItem(lastItem)
                } else {
                    homeItems.removeAll { it === folder }
                    activity.homeView.removeItemView(folder)
                }
            } else {
                refreshFolderIconsOnHome(folder)
            }
            activity.saveHomeState()
        } catch (e: Exception) {
            folder.folderItems.clear()
            folder.folderItems.addAll(backupFolderItems)
            homeItems.clear()
            homeItems.addAll(backupHomeItems)
            activity.homeView.refreshIcons(activity.model, activity.allApps)
        }
    }

    fun refreshFolderIconsOnHome(folder: HomeItem) {
        if (activity.homeView.childCount == 0) return
        val pagesContainer = activity.homeView.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until pagesContainer.childCount) {
            val page = pagesContainer.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until page.childCount) {
                val v = page.getChildAt(j)
                if (v.tag === folder) {
                    val grid = findGridLayout(v as? ViewGroup ?: continue)
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
