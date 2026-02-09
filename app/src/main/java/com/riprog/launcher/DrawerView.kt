package com.riprog.launcher

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
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class DrawerView(context: Context) : LinearLayout(context) {
    private val gridView: GridView
    private val adapter: AppAdapter
    private var longClickListener: OnAppLongClickListener? = null
    private var allApps: List<AppItem> = mutableListOf()

    fun interface OnAppLongClickListener {
        fun onAppLongClick(app: AppItem)
    }

    fun setOnAppLongClickListener(listener: OnAppLongClickListener?) {
        this.longClickListener = listener
    }

    private var filteredApps: List<AppItem> = mutableListOf()
    private var model: LauncherModel? = null
    private val settingsManager: SettingsManager = SettingsManager(context)
    private val searchBar: EditText
    private val indexBar: IndexBar

    private class IndexBar(context: Context) : LinearLayout(context) {
        override fun performClick(): Boolean {
            return super.performClick()
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.glass_bg)
        setPadding(0, dpToPx(48), 0, 0)

        searchBar = EditText(context)
        searchBar.setHint(R.string.search_hint)
        searchBar.setHintTextColor(context.getColor(R.color.foreground_dim))
        searchBar.setTextColor(context.getColor(R.color.foreground))
        searchBar.setBackgroundColor(context.getColor(R.color.search_background))
        searchBar.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        searchBar.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
        searchBar.compoundDrawablePadding = dpToPx(12)
        searchBar.compoundDrawables[0]?.setTint(context.getColor(R.color.foreground_dim))
        searchBar.isSingleLine = true
        searchBar.gravity = Gravity.CENTER_VERTICAL
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable) {}
        })
        addView(searchBar)

        val contentFrame = FrameLayout(context)
        addView(contentFrame, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        gridView = GridView(context)
        gridView.numColumns = 4
        gridView.verticalSpacing = dpToPx(16)
        gridView.setPadding(dpToPx(8), dpToPx(16), dpToPx(32), dpToPx(16))
        gridView.isVerticalScrollBarEnabled = false
        contentFrame.addView(gridView)

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
                    if (index >= 0 && index < childCount) {
                        val child = indexBar.getChildAt(index)
                        if (child is TextView) {
                            scrollToLetter(child.text.toString())
                        }
                    }
                }
                true
            } else {
                false
            }
        }
        val indexParams = FrameLayout.LayoutParams(dpToPx(30), ViewGroup.LayoutParams.MATCH_PARENT)
        indexParams.gravity = Gravity.END
        contentFrame.addView(indexBar, indexParams)
        setupIndexBar()

        adapter = AppAdapter()
        gridView.adapter = adapter

        gridView.setOnItemClickListener { _, _, position, _ ->
            val item = filteredApps[position]
            val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
            if (intent != null) context.startActivity(intent)
        }
        gridView.setOnItemLongClickListener { _, _, position, _ ->
            longClickListener?.let {
                val item = filteredApps[position]
                it.onAppLongClick(item)
                true
            } ?: false
        }
    }

    fun setApps(apps: List<AppItem>, model: LauncherModel?) {
        this.allApps = apps
        this.model = model
        sortAppsAlphabetically()
        filter(searchBar.text.toString())
    }

    private fun sortAppsAlphabetically() {
        allApps = allApps.sortedWith { a, b -> a.label.compareTo(b.label, ignoreCase = true) }
    }

    private fun setupIndexBar() {
        indexBar.removeAllViews()
        val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (letter in alphabet) {
            if (letter.isEmpty()) continue
            val tv = TextView(context)
            tv.text = letter
            tv.textSize = 10f
            tv.gravity = Gravity.CENTER
            tv.setTextColor(context.getColor(R.color.foreground_dim))
            tv.setPadding(0, dpToPx(2), 0, dpToPx(2))
            indexBar.addView(tv)
        }
    }

    private fun scrollToLetter(letter: String) {
        for (i in filteredApps.indices) {
            if (filteredApps[i].label.uppercase(Locale.getDefault()).startsWith(letter)) {
                gridView.setSelection(i)
                break
            }
        }
    }

    fun setColumns(columns: Int) {
        gridView.numColumns = columns
    }

    fun isAtTop(): Boolean {
        if (gridView.childCount == 0) return true
        return gridView.firstVisiblePosition == 0 && gridView.getChildAt(0).top >= gridView.paddingTop
    }

    fun setAccentColor(color: Int) {
        searchBar.setHintTextColor(color and 0x80FFFFFF.toInt())
    }

    fun filter(query: String?) {
        filteredApps = LauncherModel.filterApps(allApps, query)
        adapter.notifyDataSetChanged()
    }

    fun onOpen() {
        searchBar.setText("")
        searchBar.clearFocus()
        adapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private class ViewHolder {
        var icon: ImageView? = null
        var label: TextView? = null
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredApps.size
        override fun getItem(position: Int): Any = filteredApps[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            val holder: ViewHolder
            val scale = settingsManager.iconScale
            if (view == null) {
                val itemLayout = LinearLayout(context)
                itemLayout.orientation = VERTICAL
                itemLayout.gravity = Gravity.CENTER

                val icon = ImageView(context)
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                val size = (baseSize * scale).toInt()
                itemLayout.addView(icon, LayoutParams(size, size))

                val label = TextView(context)
                label.setTextColor(context.getColor(R.color.foreground_dim))
                label.textSize = 10 * scale
                label.gravity = Gravity.CENTER
                label.maxLines = 1
                label.ellipsize = TextUtils.TruncateAt.END
                itemLayout.addView(label)

                view = itemLayout
                holder = ViewHolder()
                holder.icon = icon
                holder.label = label
                view.tag = holder
            } else {
                holder = view.tag as ViewHolder
                val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                val size = (baseSize * scale).toInt()
                if (holder.icon?.layoutParams?.width != size) {
                    holder.icon?.layoutParams?.width = size
                    holder.icon?.layoutParams?.height = size
                    holder.icon?.requestLayout()
                    holder.label?.textSize = 10 * scale
                }
            }

            val item = filteredApps[position]
            holder.label?.text = item.label
            holder.icon?.setImageBitmap(null)
            holder.icon?.tag = item.packageName
            model?.loadIcon(item) { bitmap ->
                if (bitmap != null && item.packageName == holder.icon?.tag) {
                    holder.icon?.setImageBitmap(bitmap)
                }
            }

            return view!!
        }
    }
}
