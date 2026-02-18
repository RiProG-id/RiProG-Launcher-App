package com.riprog.launcher.ui.drawer

import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isEmpty
import com.riprog.launcher.R
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.repository.AppLoader
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import android.annotation.SuppressLint
import com.riprog.launcher.ui.common.ThemeUtils
import java.util.Locale

@SuppressLint("ViewConstructor")
class DrawerView(context: Context, private val settingsManager: LauncherPreferences) : LinearLayout(context) {
    private val listView: ListView
    private val adapter: AppRowAdapter
    private var longClickListener: OnAppLongClickListener? = null
    private var allApps: List<AppItem> = mutableListOf()

    interface OnAppLongClickListener {
        fun onAppLongClick(app: AppItem)
    }

    fun setOnAppLongClickListener(listener: OnAppLongClickListener?) {
        this.longClickListener = listener
    }

    private var filteredApps: List<AppItem> = mutableListOf()
    private var appLoader: AppLoader? = null
    private val searchBar: EditText
    private val indexBar: IndexBar

    private class IndexBar(context: Context) : LinearLayout(context) {
        override fun performClick(): Boolean {
            return super.performClick()
        }
    }

    private var insetTop = 0
    private var insetBottom = 0
    private var insetLeft = 0
    private var insetRight = 0
    private var numColumns = 4

    init {
        orientation = VERTICAL
        background = ThemeUtils.getGlassDrawable(context, settingsManager)

        val contentFrame = FrameLayout(context)
        addView(contentFrame, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        listView = ListView(context)
        listView.divider = null
        listView.selector = AppCompatResources.getDrawable(context, android.R.color.transparent)
        listView.isVerticalScrollBarEnabled = false
        contentFrame.addView(listView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val searchContainer = LinearLayout(context)
        searchContainer.orientation = HORIZONTAL
        searchContainer.gravity = Gravity.CENTER_VERTICAL
        searchContainer.background = ThemeUtils.getGlassDrawable(context, settingsManager, 12f)
        searchContainer.setPadding(dpToPx(12), 0, dpToPx(12), 0)

        searchBar = EditText(context)
        searchBar.setHint(R.string.search_hint)
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
        searchBar.setHintTextColor(adaptiveColor and 0x80FFFFFF.toInt())
        searchBar.setTextColor(adaptiveColor)
        searchBar.background = null
        searchBar.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12))
        searchBar.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
        searchBar.compoundDrawablePadding = dpToPx(12)
        searchBar.compoundDrawables[0]?.setTint(adaptiveColor and 0x80FFFFFF.toInt())
        searchBar.setSingleLine(true)
        searchBar.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        searchBar.gravity = Gravity.CENTER_VERTICAL
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable) {}
        })

        searchContainer.addView(searchBar, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val headerContainer = LinearLayout(context)
        headerContainer.orientation = VERTICAL
        val searchContainerParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val searchMargin = dpToPx(16)
        searchContainerParams.setMargins(searchMargin, searchMargin, searchMargin, searchMargin)
        headerContainer.addView(searchContainer, searchContainerParams)

        listView.addHeaderView(headerContainer, null, false)

        adapter = AppRowAdapter()
        listView.adapter = adapter

        indexBar = IndexBar(context)
        indexBar.orientation = VERTICAL
        indexBar.gravity = Gravity.CENTER
        indexBar.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    v.performClick()
                }
                val y = event.y
                val childCount = indexBar.childCount
                if (childCount > 0) {
                    val itemHeight = indexBar.height.toFloat() / childCount
                    val index = (y / itemHeight).toInt()
                    if (index in 0 until childCount) {
                        val child = indexBar.getChildAt(index)
                        if (child is TextView) {
                            scrollToLetter(child.text.toString())
                        }
                    }
                }
                true
            } else false
        }
        val indexParams = FrameLayout.LayoutParams(dpToPx(30), ViewGroup.LayoutParams.MATCH_PARENT)
        indexParams.gravity = Gravity.END
        contentFrame.addView(indexBar, indexParams)
        setupIndexBar()
    }

    fun setApps(apps: List<AppItem>, appLoader: AppLoader?) {
        this.allApps = apps
        this.appLoader = appLoader
        sortAppsAlphabetically()
        filter(searchBar.text.toString())
    }

    private fun sortAppsAlphabetically() {
        allApps = allApps.sortedWith { a, b -> a.label.compareTo(b.label, ignoreCase = true) }
    }

    private fun setupIndexBar() {
        indexBar.removeAllViews()
        val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("").filter { it.isNotEmpty() }
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
        for (letter in alphabet) {
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
                val row = i / numColumns
                listView.setSelection(row + listView.headerViewsCount)
                break
            }
        }
    }

    fun setColumns(columns: Int) {
        this.numColumns = columns
        adapter.notifyDataSetChanged()
    }

    fun isAtTop(): Boolean {
        if (listView.isEmpty()) return true
        return listView.firstVisiblePosition == 0 && listView.getChildAt(0).top >= 0
    }

    fun setAccentColor(color: Int) {
        searchBar.setHintTextColor(color and 0x80FFFFFF.toInt())
    }

    fun filter(query: String) {
        filteredApps = AppLoader.filterApps(allApps, query)
        adapter.notifyDataSetChanged()
    }

    fun setSystemInsets(left: Int, top: Int, right: Int, bottom: Int) {
        this.insetLeft = left
        this.insetTop = top
        this.insetRight = right
        this.insetBottom = bottom
        updatePadding()
    }

    private fun updatePadding() {
        setPadding(insetLeft, insetTop + dpToPx(8), insetRight, 0)
        listView.setPadding(dpToPx(8), dpToPx(16), dpToPx(32), insetBottom + dpToPx(16))
        listView.clipToPadding = false
    }

    fun onOpen() {
        background = ThemeUtils.getGlassDrawable(context, settingsManager)
        updatePadding()
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
        searchBar.setTextColor(adaptiveColor)
        searchBar.setHintTextColor(adaptiveColor and 0x80FFFFFF.toInt())
        searchBar.compoundDrawables[0]?.setTint(adaptiveColor and 0x80FFFFFF.toInt())
        setupIndexBar()
        searchBar.setText("")
        searchBar.clearFocus()
        listView.setSelection(0)
        adapter.notifyDataSetChanged()
    }

    fun onClose() {
        searchBar.setText("")
        filteredApps = emptyList()
        adapter.notifyDataSetChanged()
    }

    fun refreshTheme() {
        background = ThemeUtils.getGlassDrawable(context, settingsManager)
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
        searchBar.setTextColor(adaptiveColor)
        searchBar.setHintTextColor(adaptiveColor and 0x80FFFFFF.toInt())
        searchBar.compoundDrawables[0]?.setTint(adaptiveColor and 0x80FFFFFF.toInt())
        setupIndexBar()
        adapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private inner class AppRowAdapter : BaseAdapter() {
        private var lastScale = -1f
        private var lastSize = -1

        override fun getCount(): Int {
            return Math.ceil(filteredApps.size.toDouble() / numColumns).toInt()
        }

        override fun getItem(position: Int): Any? = null
        override fun getItemId(position: Int): Long = position.toLong()

        private inner class ViewHolder(val rowLayout: LinearLayout, var columns: Int) {
            val itemHolders = mutableListOf<ItemViewHolder>()

            init {
                rebuild()
            }

            fun rebuild() {
                rowLayout.removeAllViews()
                itemHolders.clear()
                for (i in 0 until numColumns) {
                    val ivh = ItemViewHolder()
                    rowLayout.addView(ivh.root, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    itemHolders.add(ivh)
                }
                this.columns = numColumns
            }
        }

        private inner class ItemViewHolder {
            val root: FrameLayout = FrameLayout(context)
            val container: LinearLayout = LinearLayout(context)
            val icon: ImageView
            val label: TextView

            init {
                container.orientation = VERTICAL
                container.gravity = Gravity.CENTER

                icon = ImageView(context)
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                container.addView(icon)

                label = TextView(context)
                label.setTextColor(ThemeUtils.getAdaptiveColor(context, settingsManager, true))
                label.gravity = Gravity.CENTER
                label.maxLines = 1
                label.ellipsize = android.text.TextUtils.TruncateAt.END
                container.addView(label)

                root.addView(container)
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: ViewHolder
            val rowView: View
            if (convertView == null) {
                val rowLayout = LinearLayout(context)
                rowLayout.orientation = HORIZONTAL
                rowLayout.setPadding(0, dpToPx(8), 0, dpToPx(8))
                holder = ViewHolder(rowLayout, numColumns)
                rowView = rowLayout
                rowView.tag = holder
            } else {
                holder = convertView.tag as ViewHolder
                rowView = convertView
                if (holder.columns != numColumns) {
                    holder.rebuild()
                }
            }

            if (lastScale < 0) {
                lastScale = settingsManager.iconScale
                lastSize = (resources.getDimensionPixelSize(R.dimen.grid_icon_size) * lastScale).toInt()
            }

            val start = position * numColumns
            for (i in 0 until numColumns) {
                val ivh = holder.itemHolders[i]
                val index = start + i

                if (index < filteredApps.size) {
                    val item = filteredApps[index]
                    ivh.root.visibility = VISIBLE
                    ivh.label.text = item.label
                    ivh.label.textSize = 10 * lastScale
                    ivh.label.setTextColor(ThemeUtils.getAdaptiveColor(context, settingsManager, true))

                    val lp = ivh.icon.layoutParams as LayoutParams
                    if (lp.width != lastSize) {
                        lp.width = lastSize
                        lp.height = lastSize
                        ivh.icon.layoutParams = lp
                    }

                    ivh.root.setOnClickListener {
                        val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                        if (intent != null) context.startActivity(intent)
                    }
                    ivh.root.setOnLongClickListener {
                        if (longClickListener != null) {
                            longClickListener!!.onAppLongClick(item)
                            true
                        } else false
                    }

                    ivh.icon.setImageDrawable(null)
                    appLoader?.loadIcon(item, object : AppLoader.OnIconLoadedListener {
                        override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                            if (icon != null) {
                                ivh.icon.setImageBitmap(icon)
                            }
                        }
                    })
                } else {
                    ivh.root.visibility = INVISIBLE
                    ivh.root.setOnClickListener(null)
                    ivh.root.setOnLongClickListener(null)
                }
            }
            return rowView
        }

        override fun notifyDataSetChanged() {
            lastScale = -1f
            super.notifyDataSetChanged()
        }
    }
}
