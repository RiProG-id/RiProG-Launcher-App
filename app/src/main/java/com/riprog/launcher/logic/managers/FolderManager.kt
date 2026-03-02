package com.riprog.launcher.logic.managers

import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.ui.views.home.HomeView

import androidx.core.view.ViewCompat
import android.content.ClipData
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.GridLayout
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import kotlin.math.*

class FolderManager(private val activity: MainActivity, private val settingsManager: SettingsManager) {
    private var currentFolderOverlay: View? = null
    private var isProcessingDrop = false
    private var activeRecyclerView: RecyclerView? = null
    private var activeFolderItem: HomeItem? = null
    private var activeDraggedItem: HomeItem? = null
    private var isDraggingInternal = false

    fun openFolder(folderItem: HomeItem, folderView: View?, homeItems: MutableList<HomeItem>, allApps: List<AppItem>) {
        val wasOpen = currentFolderOverlay != null
        if (wasOpen) {
            val oldOverlay = currentFolderOverlay!!
            currentFolderOverlay = null
            activity.mainLayout.removeView(oldOverlay)
        }

        val container: FrameLayout = object : FrameLayout(activity) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }
        container.setBackgroundColor(android.graphics.Color.TRANSPARENT)
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

        val isAcrylic = settingsManager.isAcrylic

        val overlay = LinearLayout(activity)
        overlay.orientation = LinearLayout.VERTICAL
        overlay.background = ThemeUtils.getThemedSurface(activity, settingsManager, 12f)
        overlay.elevation = if (isAcrylic) dpToPx(16f).toFloat() else dpToPx(2f).toFloat()
        overlay.setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(24f))
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
        titleEdit.isVisible = false

        val finishRename = {
            val newName = titleEdit.text.toString()
            folderItem.folderName = newName
            titleText.text = if (newName.isEmpty()) "Folder" else newName
            titleEdit.isVisible = false
            titleText.isVisible = true
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
            titleText.isVisible = false
            titleEdit.isVisible = true
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
            if (titleEdit.isVisible) {
                finishRename()
            }
        }

        val recyclerView = RecyclerView(activity)
        activeRecyclerView = recyclerView
        activeFolderItem = folderItem
        recyclerView.layoutManager = GridLayoutManager(activity, 4)
        recyclerView.itemAnimator?.moveDuration = 150
        val adapter = FolderAdapter(folderItem.folderItems)
        recyclerView.adapter = adapter
        overlay.addView(recyclerView)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.setMargins(dpToPx(24f), 0, dpToPx(24f), 0)
        container.addView(overlay, lp)

        activity.mainLayout.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        currentFolderOverlay = container
        activity.updateContentBlur()
    }

    private inner class FolderViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container) {
        fun bind(item: HomeItem, isDragged: Boolean, onLongClick: (View) -> Unit) {
            val subView = if (container.isNotEmpty()) {
                container.getChildAt(0)
            } else {
                val v = activity.itemViewFactory.createAppView(item, activity.allApps)
                container.addView(v)
                v
            }

            if (subView.tag != item) {
                updateAppView(subView, item)
                subView.tag = item
            }

            subView.isVisible = !isDragged

            subView.setOnClickListener {
                activity.handleAppLaunch(item.packageName!!)
                closeFolder()
            }

            subView.setOnLongClickListener {
                onLongClick(subView)
                true
            }
        }

        private fun updateAppView(view: View, item: HomeItem) {
            val layout = view as? LinearLayout ?: return
            val iconView = layout.getChildAt(0) as? ImageView ?: return
            val labelView = layout.getChildAt(1) as? TextView ?: return

            val packageName = item.packageName ?: return
            val app = activity.allApps.find { it.packageName == packageName }

            val adaptiveColor = ThemeUtils.getAdaptiveColor(activity, settingsManager, true)
            labelView.setTextColor(adaptiveColor)

            if (app != null) {
                activity.model.loadIcon(app) { bitmap -> iconView.setImageBitmap(bitmap) }
                labelView.text = app.label
            } else {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
                labelView.text = "..."
            }
        }
    }

    private inner class FolderAdapter(val items: MutableList<HomeItem>) : RecyclerView.Adapter<FolderViewHolder>() {
        var draggedItem: HomeItem? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
            val folderPadding = dpToPx(8f)
            val container = FrameLayout(parent.context)
            val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(folderPadding, folderPadding, folderPadding, folderPadding)
            container.layoutParams = lp
            return FolderViewHolder(container)
        }

        override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, item === draggedItem) { view ->
                activity.folderManager.startInternalFolderDrag(view, item)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    fun closeFolder() {
        if (currentFolderOverlay != null) {
            val overlay = currentFolderOverlay!!
            currentFolderOverlay = null
            activity.mainLayout.removeView(overlay)
            activity.updateContentBlur()
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
        for (page in activity.homeView.pages) {
            for (j in 0 until page.childCount) {
                val v = page.getChildAt(j)
                if (v.tag === folder) {
                    val grid = findGridLayout(v as? ViewGroup ?: continue)
                    if (grid != null) activity.refreshFolderPreview(folder, grid)
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

    fun startInternalFolderDrag(view: View, item: HomeItem) {
        activeDraggedItem = item
        isDraggingInternal = true

        val containerLocation = IntArray(2)
        currentFolderOverlay?.getLocationInWindow(containerLocation)

        val initialTouchX = view.x + view.width / 2f + containerLocation[0]
        val initialTouchY = view.y + view.height / 2f + containerLocation[1]

        activity.freeformInteraction.showTransformOverlay(view, initialTouchX, initialTouchY)
    }

    fun checkFolderReorderOrExit(x: Float, y: Float) {
        if (!isDraggingInternal || activeDraggedItem == null) return

        val overlay = currentFolderOverlay ?: return
        val overlayLocation = IntArray(2)
        overlay.getLocationInWindow(overlayLocation)

        val isOutside = x < overlayLocation[0] || x > overlayLocation[0] + overlay.width ||
                        y < overlayLocation[1] || y > overlayLocation[1] + overlay.height

        if (isOutside) {
            val item = activeDraggedItem!!
            val folder = activeFolderItem!!

            isDraggingInternal = false
            activeDraggedItem = null

            closeFolder()
            removeFromFolder(folder, item, activity.homeItems)

            if (!activity.homeItems.contains(item)) {
                item.page = activity.homeView.currentPage
                activity.homeItems.add(item)
            }

            item.visualOffsetX = -1f
            item.visualOffsetY = -1f
            activity.saveHomeState()
            return
        }

        val rv = activeRecyclerView ?: return
        val adapter = rv.adapter as? FolderAdapter ?: return

        val rvLocation = IntArray(2)
        rv.getLocationInWindow(rvLocation)
        val relativeX = x - rvLocation[0]
        val relativeY = y - rvLocation[1]

        var nearestView: View? = null
        var minDistance = Float.MAX_VALUE
        val threshold = dpToPx(80f).toFloat()

        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val centerX = child.x + child.width / 2f
            val centerY = child.y + child.height / 2f
            val dist = sqrt((relativeX - centerX).toDouble().pow(2.0) + (relativeY - centerY).toDouble().pow(2.0)).toFloat()
            if (dist < minDistance && dist < threshold) {
                minDistance = dist
                nearestView = child
            }
        }

        if (nearestView != null) {
            val targetIndex = rv.getChildAdapterPosition(nearestView)
            val currentIndex = adapter.items.indexOf(activeDraggedItem)
            if (targetIndex != RecyclerView.NO_POSITION && targetIndex != currentIndex) {
                val item = adapter.items.removeAt(currentIndex)
                adapter.items.add(targetIndex, item)
                adapter.notifyItemMoved(currentIndex, targetIndex)
                refreshFolderIconsOnHome(activeFolderItem!!)
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, activity.resources.displayMetrics
        ).toInt()
    }
}
