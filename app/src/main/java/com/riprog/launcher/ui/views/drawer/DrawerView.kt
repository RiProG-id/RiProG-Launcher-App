package com.riprog.launcher.ui.views.drawer

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.R

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class DrawerView(context: Context) : LinearLayout(context) {
    private val recyclerView: RecyclerView
    private val adapter: AppAdapter
    private val layoutManager: GridLayoutManager
    private var longClickListener: OnAppLongClickListener? = null
    private var allApps: List<AppItem> = ArrayList()
    private var filteredApps: MutableList<AppItem> = ArrayList()
    private var model: AppRepository? = null
    private val settingsManager: SettingsManager = SettingsManager(context)
    private var searchBar: EditText? = null
    private val indexBar: IndexBar
    private var highlightLetter: String? = null
    private var pendingAccentColor: Int? = null
    private var currentSearchQuery: String = ""

    interface OnAppLongClickListener {
        fun onAppLongClick(app: AppItem)
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
        orientation = VERTICAL
        background = ThemeUtils.getGlassDrawable(context, settingsManager, 0f)
        setPadding(0, dpToPx(48), 0, 0)

        val contentFrame = FrameLayout(context)
        addView(contentFrame, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        recyclerView = RecyclerView(context)
        layoutManager = GridLayoutManager(context, 4)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position == 0) layoutManager.spanCount else 1
            }
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.setPadding(dpToPx(8), dpToPx(16), dpToPx(48), dpToPx(16))
        recyclerView.clipToPadding = false
        contentFrame.addView(recyclerView)

        indexBar = IndexBar(context)
        indexBar.orientation = VERTICAL
        indexBar.gravity = Gravity.CENTER
        indexBar.setOnTouchListener { v: View, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    v.performClick()
                }
                val y = event.y
                val ib = v as ViewGroup
                val count = ib.childCount
                if (count > 0) {
                    val itemHeight = ib.height.toFloat() / count
                    val index = (y / itemHeight).toInt()
                    if (index >= 0 && index < count) {
                        val child = ib.getChildAt(index)
                        if (child is TextView) {
                            val letter = child.text.toString()
                            scrollToLetter(letter)
                            updateHighlight(letter)
                        }
                    }
                }
                return@setOnTouchListener true
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                updateHighlight(null)
                return@setOnTouchListener true
            }
            false
        }
        // Increased width for better precision as requested
        val indexParams = FrameLayout.LayoutParams(dpToPx(48), ViewGroup.LayoutParams.MATCH_PARENT)
        indexParams.gravity = Gravity.END
        contentFrame.addView(indexBar, indexParams)
        setupIndexBar()

        adapter = AppAdapter()
        recyclerView.adapter = adapter
    }

    private fun updateHighlight(letter: String?) {
        highlightLetter = letter?.uppercase()
        adapter.notifyItemRangeChanged(1, adapter.itemCount - 1)
    }

    fun setApps(apps: List<AppItem>, model: AppRepository) {
        this.allApps = apps
        this.model = model
        sortAppsAlphabetically()
        filter(currentSearchQuery)
    }

    private fun sortAppsAlphabetically() {
        allApps = allApps.sortedWith { a: AppItem, b: AppItem ->
            a.label.compareTo(b.label, ignoreCase = true)
        }
    }

    private fun setupIndexBar() {
        indexBar.removeAllViews()
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
        val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (letter in alphabet) {
            if (letter.isEmpty()) continue
            val tv = TextView(context)
            tv.text = letter
            tv.textSize = 10f
            tv.gravity = Gravity.CENTER
            tv.setTextColor(adaptiveColor and 0x80FFFFFF.toInt())
            tv.setPadding(0, dpToPx(2), 0, dpToPx(2))
            indexBar.addView(tv)
        }
    }

    private fun scrollToLetter(letter: String) {
        for (i in filteredApps.indices) {
            if (filteredApps[i].label.uppercase(Locale.getDefault()).startsWith(letter)) {
                layoutManager.scrollToPositionWithOffset(i + 1, 0)
                break
            }
        }
    }

    fun setColumns(columns: Int) {
        layoutManager.spanCount = columns
    }

    fun isAtTop(): Boolean {
        return !recyclerView.canScrollVertically(-1)
    }

    fun setAccentColor(color: Int) {
        pendingAccentColor = color
        searchBar?.setHintTextColor(color and 0x80FFFFFF.toInt())
    }

    fun filter(query: String?) {
        currentSearchQuery = query ?: ""
        filteredApps = AppRepository.filterApps(allApps, currentSearchQuery).toMutableList()
        adapter.notifyDataSetChanged()
    }

    fun onOpen() {
        currentSearchQuery = ""
        searchBar?.setText("")
        searchBar?.clearFocus()
        adapter.notifyDataSetChanged()
    }

    fun onClose() {
        currentSearchQuery = ""
        searchBar?.setText("")
        filteredApps.clear()
        adapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private inner class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var icon: ImageView? = null
        var label: TextView? = null
        var lastScale: Float = 0f
    }

    private inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val VIEW_TYPE_SEARCH = 0
        private val VIEW_TYPE_APP = 1

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) VIEW_TYPE_SEARCH else VIEW_TYPE_APP
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VIEW_TYPE_SEARCH) {
                val search = EditText(context)
                search.setHint(R.string.search_hint)
                val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
                search.setHintTextColor((pendingAccentColor ?: adaptiveColor) and 0x80FFFFFF.toInt())
                search.setTextColor(adaptiveColor)
                search.setBackgroundColor(context.getColor(R.color.search_background))
                search.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
                search.compoundDrawablePadding = dpToPx(12)
                search.compoundDrawables[0]?.setTint(adaptiveColor and 0x80FFFFFF.toInt())
                search.setSingleLine(true)
                search.gravity = Gravity.CENTER_VERTICAL
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                        if (s.toString() != currentSearchQuery) {
                            filter(s.toString())
                        }
                    }
                    override fun afterTextChanged(s: Editable) {}
                })
                search.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }
                searchBar = search
                val holder = SearchViewHolder(search)
                holder.setIsRecyclable(false) // Keep it stable
                return holder
            } else {
                val itemLayout = LinearLayout(context)
                itemLayout.orientation = VERTICAL
                itemLayout.gravity = Gravity.CENTER
                itemLayout.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )

                val icon = ImageView(context)
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                val scale = settingsManager.iconScale
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

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == VIEW_TYPE_APP) {
                val appHolder = holder as AppViewHolder
                val item = filteredApps[position - 1]
                val scale = settingsManager.iconScale

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
                model?.loadIcon(item) { bitmap ->
                    if (bitmap != null && item.packageName == appHolder.icon!!.tag) {
                        appHolder.icon!!.setImageBitmap(bitmap)
                    }
                }

                appHolder.itemView.setOnClickListener {
                    val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                    if (intent != null) context.startActivity(intent)
                }
                appHolder.itemView.setOnLongClickListener {
                    longClickListener?.onAppLongClick(item)
                    true
                }

                // Alphabet Navigation highlight effect
                if (highlightLetter != null) {
                    if (item.label.uppercase().startsWith(highlightLetter!!)) {
                        appHolder.itemView.alpha = 1.0f
                    } else {
                        appHolder.itemView.alpha = 0.3f
                    }
                } else {
                    appHolder.itemView.alpha = 1.0f
                }
            } else if (getItemViewType(position) == VIEW_TYPE_SEARCH) {
                val search = holder.itemView as EditText
                searchBar = search
                if (search.text.toString() != currentSearchQuery) {
                    search.setText(currentSearchQuery)
                }
            }
        }

        override fun getItemCount(): Int {
            return filteredApps.size + 1
        }
    }
}
