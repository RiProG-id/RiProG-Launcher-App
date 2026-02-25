package com.riprog.launcher.ui.views.drawer

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.theme.ThemeManager
import com.riprog.launcher.theme.ThemeStyle
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.R

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class DrawerView(context: Context) : FrameLayout(context) {
    companion object {
        private const val VIEW_TYPE_SEARCH = 0
        private const val VIEW_TYPE_APP = 1
    }
    private val backgroundView: View
    private val contentLayout: LinearLayout
    private val recyclerView: RecyclerView
    private val adapter: AppAdapter
    private var longClickListener: OnAppLongClickListener? = null
    private var allApps: List<AppItem> = ArrayList()
    private var filteredApps: MutableList<AppItem> = ArrayList()
    private var model: AppRepository? = null
    private val settingsManager: SettingsManager = SettingsManager(context)
    private lateinit var searchBar: EditText
    private val indexBar: IndexBar
    private val letterPositions = mutableMapOf<String, Int>()
    private var lastSelectedIndex = -1
    private var selectedLetter: String? = null

    interface OnAppLongClickListener {
        fun onAppLongClick(view: View, app: AppItem)
    }

    fun setOnAppLongClickListener(listener: OnAppLongClickListener?) {
        this.longClickListener = listener
    }

    private class IndexBar(context: Context) : LinearLayout(context) {
        override fun performClick(): Boolean {
            return super.performClick()
        }
    }

    init {
        // Root is now FrameLayout. We add a dedicated background layer.
        backgroundView = View(context)
        backgroundView.background = ThemeUtils.getThemedSurface(context, settingsManager, 0f)
        addView(backgroundView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Content layer
        contentLayout = LinearLayout(context)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.setPadding(0, dpToPx(48), 0, 0)
        addView(contentLayout, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val contentFrame = FrameLayout(context)
        contentLayout.addView(contentFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        recyclerView = RecyclerView(context)
        recyclerView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val layoutManager = GridLayoutManager(context, 4)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == VIEW_TYPE_SEARCH) 4 else 1
            }
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.setPadding(dpToPx(8), dpToPx(16), dpToPx(32), dpToPx(16))
        recyclerView.clipToPadding = false
        contentFrame.addView(recyclerView)

        indexBar = IndexBar(context)
        indexBar.orientation = LinearLayout.VERTICAL
        indexBar.gravity = Gravity.CENTER
        indexBar.setOnTouchListener { v: View, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        v.performClick()
                        recyclerView.stopScroll()
                    }
                    val y = event.y
                    val childCount = indexBar.childCount
                    if (childCount > 0) {
                        val itemHeight = indexBar.height.toFloat() / childCount
                        val index = (y / itemHeight).toInt().coerceIn(0, childCount - 1)
                        if (index != lastSelectedIndex) {
                            val child = indexBar.getChildAt(index)
                            if (child is TextView) {
                                highlightLetter(index)
                                scrollToLetter(child.text.toString())
                            }
                        }
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resetHighlight()
                    return@setOnTouchListener true
                }
            }
            false
        }
        val indexParams = FrameLayout.LayoutParams(dpToPx(40), ViewGroup.LayoutParams.MATCH_PARENT)
        indexParams.gravity = Gravity.END
        contentFrame.addView(indexBar, indexParams)
        setupIndexBar()

        adapter = AppAdapter()
        recyclerView.adapter = adapter
    }

    fun setApps(apps: List<AppItem>, model: AppRepository) {
        this.allApps = apps
        this.model = model
        sortAppsAlphabetically()
        filter(if (::searchBar.isInitialized) searchBar.text.toString() else "")
    }

    private fun sortAppsAlphabetically() {
        allApps = allApps.sortedWith { a: AppItem, b: AppItem ->
            a.label.compareTo(b.label, ignoreCase = true)
        }
    }

    private fun setupIndexBar() {
        indexBar.removeAllViews()
        val isMaterial = settingsManager.themeStyle == ThemeStyle.MATERIAL
        val dimColor = if (isMaterial) ThemeUtils.getOnSurfaceVariantColor(context) else context.getColor(R.color.foreground_dim)

        val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (letter in alphabet) {
            if (letter.isEmpty()) continue
            val tv = TextView(context)
            tv.text = letter
            tv.textSize = 10f
            tv.gravity = Gravity.CENTER
            tv.setTextColor(dimColor)
            tv.setPadding(0, dpToPx(2), 0, dpToPx(2))
            indexBar.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun updateLetterPositions() {
        letterPositions.clear()
        for (i in filteredApps.indices) {
            val label = filteredApps[i].label
            if (label.isEmpty()) continue
            val firstChar = label.first().uppercaseChar()
            val key = if (firstChar.isLetter()) firstChar.toString() else "#"
            if (!letterPositions.containsKey(key)) {
                letterPositions[key] = i + 1
            }
        }
    }

    private fun scrollToLetter(letter: String) {
        val pos = letterPositions[letter]
        if (pos != null) {
            (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(pos, 0)
        } else {
            val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val index = alphabet.indexOf(letter)
            if (index != -1) {
                var targetPos = -1
                for (i in index until alphabet.length) {
                    val nextLetter = alphabet[i].toString()
                    if (letterPositions.containsKey(nextLetter)) {
                        targetPos = letterPositions[nextLetter]!!
                        break
                    }
                }
                if (targetPos == -1) {
                    for (i in index downTo 0) {
                        val prevLetter = alphabet[i].toString()
                        if (letterPositions.containsKey(prevLetter)) {
                            targetPos = letterPositions[prevLetter]!!
                            break
                        }
                    }
                }
                if (targetPos != -1) {
                    (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(targetPos, 0)
                }
            }
        }
    }

    fun setColumns(columns: Int) {
        (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = columns
    }

    fun isAtTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return true
        val pos = lm.findFirstVisibleItemPosition()
        if (pos == 0) {
            val v = lm.findViewByPosition(0)
            return v != null && v.top >= recyclerView.paddingTop
        }
        return false
    }

    fun setAccentColor(color: Int) {
        if (::searchBar.isInitialized) {
            searchBar.setHintTextColor(color and 0x80FFFFFF.toInt())
        }
    }

    private fun highlightLetter(index: Int) {
        if (index == lastSelectedIndex) return
        val isMaterial = settingsManager.themeStyle == ThemeStyle.MATERIAL
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
        val dimColor = if (isMaterial) ThemeUtils.getOnSurfaceVariantColor(context) else context.getColor(R.color.foreground_dim)
        val isLiquid = settingsManager.isLiquidGlass

        if (lastSelectedIndex in 0 until indexBar.childCount) {
            val prevTv = indexBar.getChildAt(lastSelectedIndex) as? TextView
            prevTv?.let {
                it.setTextColor(dimColor)
                it.setTypeface(null, android.graphics.Typeface.NORMAL)
                if (isLiquid) {
                    it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                } else {
                    it.scaleX = 1.0f
                    it.scaleY = 1.0f
                }
            }
        }

        if (index in 0 until indexBar.childCount) {
            val currTv = indexBar.getChildAt(index) as? TextView
            currTv?.let {
                it.setTextColor(adaptiveColor)
                it.setTypeface(null, android.graphics.Typeface.BOLD)
                if (isLiquid) {
                    it.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
                } else {
                    it.scaleX = 1.2f
                    it.scaleY = 1.2f
                }
                selectedLetter = it.text.toString()
                adapter.notifyItemRangeChanged(1, adapter.itemCount - 1, "FOCUS_CHANGE")
            }
        }
        lastSelectedIndex = index
    }

    private fun resetHighlight() {
        if (lastSelectedIndex != -1) {
            val isMaterial = settingsManager.themeStyle == ThemeStyle.MATERIAL
            val dimColor = if (isMaterial) ThemeUtils.getOnSurfaceVariantColor(context) else context.getColor(R.color.foreground_dim)
            val tv = indexBar.getChildAt(lastSelectedIndex) as? TextView
            tv?.let {
                it.setTextColor(dimColor)
                it.setTypeface(null, android.graphics.Typeface.NORMAL)
                if (settingsManager.isLiquidGlass) {
                    it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                } else {
                    it.scaleX = 1.0f
                    it.scaleY = 1.0f
                }
            }
        }
        selectedLetter = null
        adapter.notifyItemRangeChanged(1, adapter.itemCount - 1, "FOCUS_CHANGE")
        lastSelectedIndex = -1
    }

    fun filter(query: String?) {
        val hasFocus = if (::searchBar.isInitialized) searchBar.hasFocus() else false
        val selectionStart = if (::searchBar.isInitialized) searchBar.selectionStart else -1
        val selectionEnd = if (::searchBar.isInitialized) searchBar.selectionEnd else -1

        val newFilteredApps = AppRepository.filterApps(allApps, query)
        val diffCallback = AppDiffCallback(filteredApps, newFilteredApps)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        filteredApps.clear()
        filteredApps.addAll(newFilteredApps)
        diffResult.dispatchUpdatesTo(adapter)
        updateLetterPositions()

        if (hasFocus && ::searchBar.isInitialized && !searchBar.hasFocus()) {
            searchBar.requestFocus()
            if (selectionStart != -1 && selectionEnd != -1) {
                searchBar.setSelection(
                    selectionStart.coerceAtMost(searchBar.text.length),
                    selectionEnd.coerceAtMost(searchBar.text.length)
                )
            }
        }
    }

    private class AppDiffCallback(
        private val oldList: List<AppItem>,
        private val newList: List<AppItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size + 1
        override fun getNewListSize(): Int = newList.size + 1

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            if (oldItemPosition == 0 && newItemPosition == 0) return true
            if (oldItemPosition == 0 || newItemPosition == 0) return false
            val oldItem = oldList[oldItemPosition - 1]
            val newItem = newList[newItemPosition - 1]
            return oldItem.packageName == newItem.packageName && oldItem.className == newItem.className
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            if (oldItemPosition == 0 && newItemPosition == 0) return true
            if (oldItemPosition == 0 || newItemPosition == 0) return false
            val oldItem = oldList[oldItemPosition - 1]
            val newItem = newList[newItemPosition - 1]
            return oldItem.label == newItem.label
        }
    }

    fun onOpen() {
        if (::searchBar.isInitialized) {
            searchBar.setText("")
            searchBar.clearFocus()
        }
        adapter.notifyDataSetChanged()
    }

    fun onClose() {
        if (::searchBar.isInitialized) {
            searchBar.setText("")
        }
        filteredApps.clear()
        adapter.notifyDataSetChanged()
    }

    fun refreshTheme() {
        val isMaterial = settingsManager.themeStyle == ThemeStyle.MATERIAL
        backgroundView.background = ThemeUtils.getThemedSurface(context, settingsManager, 0f)
        setupIndexBar()
        if (::searchBar.isInitialized) {
            val hintColor = if (isMaterial) ThemeUtils.getOnSurfaceVariantColor(context) else context.getColor(R.color.foreground_dim)
            val bgColor = if (isMaterial) ThemeUtils.getSurfaceVariantColor(context) else context.getColor(R.color.search_background)
            val textColor = if (isMaterial) ThemeUtils.getOnSurfaceColor(context) else ThemeUtils.getAdaptiveColor(context, settingsManager, true)

            searchBar.setHintTextColor(hintColor)
            searchBar.setTextColor(textColor)

            if (isMaterial) {
                val gd = GradientDrawable()
                gd.setColor(bgColor)
                gd.cornerRadius = dpToPx(28).toFloat()
                searchBar.background = gd
            } else {
                searchBar.setBackgroundColor(bgColor)
            }

            if (searchBar.compoundDrawables[0] != null) {
                searchBar.compoundDrawables[0].setTint(hintColor)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var icon: ImageView? = null
        var label: TextView? = null
        var lastScale: Float = 0f
    }

    private inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int {
            return if (position == 0) VIEW_TYPE_SEARCH else VIEW_TYPE_APP
        }

        override fun getItemCount(): Int {
            return filteredApps.size + 1
        }

        private fun updateItemFocus(holder: AppViewHolder, position: Int) {
            if (position == 0) return
            val item = filteredApps[position - 1]
            val firstChar = item.label.firstOrNull()?.uppercaseChar()
            val itemLetter = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"

            val isFocused = selectedLetter == null || itemLetter == selectedLetter
            holder.itemView.alpha = if (isFocused) 1.0f else 0.3f
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VIEW_TYPE_SEARCH) {
                if (::searchBar.isInitialized) {
                    (searchBar.parent as? ViewGroup)?.removeView(searchBar)
                    return object : RecyclerView.ViewHolder(searchBar) {}
                }
                val isMaterial = settingsManager.themeStyle == ThemeStyle.MATERIAL
                val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
                val hintColor = if (isMaterial) ThemeUtils.getOnSurfaceVariantColor(context) else context.getColor(R.color.foreground_dim)
                val bgColor = if (isMaterial) ThemeUtils.getSurfaceVariantColor(context) else context.getColor(R.color.search_background)
                val textColor = if (isMaterial) ThemeUtils.getOnSurfaceColor(context) else adaptiveColor

                searchBar = EditText(context)
                searchBar.id = View.generateViewId()
                searchBar.setHint(R.string.search_hint)
                searchBar.setHintTextColor(hintColor)
                searchBar.setTextColor(textColor)

                if (isMaterial) {
                    val gd = GradientDrawable()
                    gd.setColor(bgColor)
                    gd.cornerRadius = dpToPx(28).toFloat()
                    searchBar.background = gd
                } else {
                    searchBar.setBackgroundColor(bgColor)
                }

                searchBar.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                searchBar.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
                searchBar.compoundDrawablePadding = dpToPx(12)
                if (searchBar.compoundDrawables[0] != null) {
                    searchBar.compoundDrawables[0].setTint(hintColor)
                }
                searchBar.setSingleLine(true)
                searchBar.gravity = Gravity.CENTER_VERTICAL
                searchBar.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                        filter(s.toString())
                    }
                    override fun afterTextChanged(s: Editable) {}
                })
                val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, dpToPx(16))
                searchBar.layoutParams = lp
                return object : RecyclerView.ViewHolder(searchBar) {}
            } else {
                val scale = settingsManager.iconScale
                val itemLayout = LinearLayout(context)
                itemLayout.orientation = LinearLayout.VERTICAL
                itemLayout.gravity = Gravity.CENTER
                itemLayout.isClickable = true
                itemLayout.isFocusable = true
                itemLayout.setPadding(0, dpToPx(8), 0, dpToPx(8))

                val icon = ImageView(context)
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                val size = (baseSize * scale).toInt()
                itemLayout.addView(icon, LayoutParams(size, size))

                val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
                val label = TextView(context)
                label.setTextColor(adaptiveColor)
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
                label.gravity = Gravity.CENTER
                label.maxLines = 1
                label.ellipsize = TextUtils.TruncateAt.END
                itemLayout.addView(label)

                val holder = AppViewHolder(itemLayout)
                holder.icon = icon
                holder.label = label
                holder.lastScale = scale
                return holder
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty() && payloads.contains("FOCUS_CHANGE")) {
                if (holder is AppViewHolder) {
                    updateItemFocus(holder, position)
                }
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == VIEW_TYPE_APP) {
                val appHolder = holder as AppViewHolder
                val item = filteredApps[position - 1]
                val scale = settingsManager.iconScale

                updateItemFocus(appHolder, position)

                if (appHolder.lastScale != scale) {
                    val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                    val size = (baseSize * scale).toInt()
                    val lp = appHolder.icon!!.layoutParams
                    lp.width = size
                    lp.height = size
                    appHolder.icon!!.layoutParams = lp
                    appHolder.label!!.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
                    appHolder.lastScale = scale
                }

                appHolder.label!!.text = item.label
                appHolder.icon!!.setImageBitmap(null)
                appHolder.icon!!.tag = item.packageName
                if (model != null) {
                    model!!.loadIcon(
                        item,
                        ThemeManager.isMaterialYouIconsEnabled(settingsManager),
                        ThemeManager.getIconTint(context)
                    ) { bitmap ->
                        if (bitmap != null && item.packageName == appHolder.icon!!.tag) {
                            appHolder.icon!!.setImageBitmap(bitmap)
                        }
                    }
                }

                appHolder.itemView.setOnClickListener {
                    val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                    if (intent != null) context.startActivity(intent)
                }

                appHolder.itemView.setOnLongClickListener {
                    if (longClickListener != null) {
                        longClickListener!!.onAppLongClick(it, item)
                        return@setOnLongClickListener true
                    }
                    false
                }
            }
        }
    }
}
