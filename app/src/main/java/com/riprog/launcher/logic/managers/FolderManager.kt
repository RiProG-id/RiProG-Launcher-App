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
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.ui.adapters.UnifiedLauncherAdapter

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
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> return true
                    MotionEvent.ACTION_UP -> {
                        if (event.y - startY > dpToPx(100f)) {
                            closeFolder()
                        }
                        return true
                    }
                }
                return true
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
            activity.homeView.refreshData(activity.homeItems)
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

        val recyclerView = RecyclerView(activity)
        val adapter = UnifiedLauncherAdapter(settingsManager, activity.model, object : UnifiedLauncherAdapter.Callback {
            override fun onItemClick(item: Any, view: View) {
                if (item is HomeItem) {
                    activity.handleAppLaunch(item.packageName!!)
                    closeFolder()
                }
            }

            override fun onItemLongClick(item: Any, view: View): Boolean {
                if (item is HomeItem) {
                    val data = ClipData.newPlainText("index", folderItem.folderItems.indexOf(item).toString())
                    val shadow = View.DragShadowBuilder(view)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        view.startDragAndDrop(data, shadow, view, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        view.startDrag(data, shadow, view, 0)
                    }
                    view.visibility = View.INVISIBLE
                    return true
                }
                return false
            }
        })
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(activity, 4)
        adapter.setItems(folderItem.folderItems)
        overlay.addView(recyclerView)

        val dragListener = View.OnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    isProcessingDrop = false
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val draggedView = event.localState as? View
                    if (draggedView != null) {
                        val rvLocation = IntArray(2)
                        recyclerView.getLocationInWindow(rvLocation)
                        val containerLocation = IntArray(2)
                        container.getLocationInWindow(containerLocation)

                        val xInWindow = event.x + containerLocation[0]
                        val yInWindow = event.y + containerLocation[1]

                        val relativeX = xInWindow - rvLocation[0]
                        val relativeY = yInWindow - rvLocation[1]

                        val targetView = recyclerView.findChildViewUnder(relativeX, relativeY)
                        if (targetView != null) {
                            val targetIndex = recyclerView.getChildAdapterPosition(targetView)
                            val currentIndex = folderItem.folderItems.indexOf(draggedView.tag as HomeItem)
                            if (targetIndex != -1 && targetIndex != currentIndex) {
                                val item = folderItem.folderItems.removeAt(currentIndex)
                                folderItem.folderItems.add(targetIndex, item)
                                adapter.notifyItemMoved(currentIndex, targetIndex)
                            }
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

                        val containerLocation = IntArray(2)
                        container.getLocationInWindow(containerLocation)

                        val dropXInWindow = x + containerLocation[0]
                        val dropYInWindow = y + containerLocation[1]

                        val overlayLocation = IntArray(2)
                        overlay.getLocationInWindow(overlayLocation)

                        if (dropXInWindow >= overlayLocation[0] && dropXInWindow <= overlayLocation[0] + overlay.width &&
                            dropYInWindow >= overlayLocation[1] && dropYInWindow <= overlayLocation[1] + overlay.height) {

                            draggedView.visibility = View.VISIBLE
                            adapter.notifyDataSetChanged()
                            activity.saveHomeState()
                        } else {
                            // Dropped outside folder bounds -> Remove from folder
                            closeFolder()
                            removeFromFolder(folderItem, draggedItem, homeItems)

                            val targetPage = activity.homeView.currentPage
                            draggedItem.page = targetPage

                            activity.homeView.refreshData(activity.homeItems)
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


    fun closeFolder() {
        if (currentFolderOverlay != null) {
            val overlay = currentFolderOverlay!!
            currentFolderOverlay = null
            activity.mainLayout.removeView(overlay)
            ThemeUtils.applyWindowBlur(activity.window, false)
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(activity.mainLayout.windowToken, 0)
            activity.homeView.refreshData(activity.homeItems)
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

            activity.homeView.refreshData(homeItems)
            activity.saveHomeState()
        } catch (e: Exception) {
            homeItems.clear()
            homeItems.addAll(backupHomeItems)
            activity.homeView.refreshData(homeItems)
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

            activity.homeView.refreshData(homeItems)
            activity.saveHomeState()
        } catch (e: Exception) {
            folder.folderItems.clear()
            folder.folderItems.addAll(backupFolderItems)
            homeItems.clear()
            homeItems.addAll(backupHomeItems)
            activity.homeView.refreshData(homeItems)
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

                } else {
                    homeItems.removeAll { it === folder }
                }
            }
            activity.homeView.refreshData(homeItems)
            activity.saveHomeState()
        } catch (e: Exception) {
            folder.folderItems.clear()
            folder.folderItems.addAll(backupFolderItems)
            homeItems.clear()
            homeItems.addAll(backupHomeItems)
            activity.homeView.refreshData(homeItems)
        }
    }

    fun refreshFolderIconsOnHome(folder: HomeItem) {
        activity.homeView.refreshData(activity.homeItems)
    }

    private fun findRecyclerView(container: ViewGroup): RecyclerView? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is RecyclerView) return child
            if (child is ViewGroup) {
                val rv = findRecyclerView(child)
                if (rv != null) return rv
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
