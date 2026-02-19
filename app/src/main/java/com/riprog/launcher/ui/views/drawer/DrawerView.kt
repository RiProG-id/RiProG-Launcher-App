package com.riprog.launcher.ui.views.drawer

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.model.LauncherSettings
import com.riprog.launcher.R

import android.content.Context
import android.content.Intent
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
import java.util.*

class DrawerView(context: Context) : LinearLayout(context) {
    private val gridView: GridView
    private val adapter: AppAdapter
    private var longClickListener: OnAppLongClickListener? = null
    private var allApps: List<AppItem> = ArrayList()
    private var filteredApps: MutableList<AppItem> = ArrayList()
    private var model: AppRepository? = null
    private var settings: LauncherSettings = LauncherSettings()
    private val searchBar: EditText
    private val indexBar: IndexBar

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
        setPadding(0, dpToPx(48), 0, 0)

        searchBar = EditText(context)
        searchBar.setHint(R.string.search_hint)
        searchBar.setBackgroundColor(context.getColor(R.color.search_background))
        searchBar.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        searchBar.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
        searchBar.compoundDrawablePadding = dpToPx(12)
        searchBar.setSingleLine(true)
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
        indexBar.setOnTouchListener { v: View, event: MotionEvent ->
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
                return@setOnTouchListener true
            }
            false
        }
        val indexParams = FrameLayout.LayoutParams(dpToPx(30), ViewGroup.LayoutParams.MATCH_PARENT)
        indexParams.gravity = Gravity.END
        contentFrame.addView(indexBar, indexParams)

        adapter = AppAdapter()
        gridView.adapter = adapter

        gridView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = filteredApps[position]
            val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
            if (intent != null) context.startActivity(intent)
        }
        gridView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            if (longClickListener != null) {
                val item = filteredApps[position]
                longClickListener!!.onAppLongClick(item)
                return@OnItemLongClickListener true
            }
            false
        }

        updateUiState()
    }

    fun updateSettings(newSettings: LauncherSettings) {
        val oldScale = settings.iconScale
        val oldColumns = settings.columns
        val oldTheme = settings.themeMode
        val oldLiquid = settings.isLiquidGlass

        this.settings = newSettings

        if (oldScale != newSettings.iconScale || oldColumns != newSettings.columns ||
            oldTheme != newSettings.themeMode || oldLiquid != newSettings.isLiquidGlass) {
            updateUiState()
        }
    }

    private fun updateUiState() {
        background = ThemeUtils.getGlassDrawable(context, settings, 0f)
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settings, true)

        searchBar.setHintTextColor(adaptiveColor and 0x80FFFFFF.toInt())
        searchBar.setTextColor(adaptiveColor)
        if (searchBar.compoundDrawables[0] != null) {
            searchBar.compoundDrawables[0].setTint(adaptiveColor and 0x80FFFFFF.toInt())
        }

        gridView.numColumns = settings.columns
        setupIndexBar()
        adapter.notifyDataSetChanged()
    }

    fun setApps(apps: List<AppItem>, model: AppRepository) {
        this.allApps = apps
        this.model = model
        sortAppsAlphabetically()
        filter(searchBar.text.toString())
    }

    private fun sortAppsAlphabetically() {
        allApps = allApps.sortedWith { a: AppItem, b: AppItem ->
            a.label.compareTo(b.label, ignoreCase = true)
        }
    }

    private fun setupIndexBar() {
        indexBar.removeAllViews()
        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settings, true)
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
        filteredApps = AppRepository.filterApps(allApps, query).toMutableList()
        adapter.notifyDataSetChanged()
    }

    fun onOpen() {
        searchBar.setText("")
        searchBar.clearFocus()
        adapter.notifyDataSetChanged()
    }

    fun onClose() {
        searchBar.setText("")
        filteredApps.clear()
        adapter.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private class ViewHolder {
        var icon: ImageView? = null
        var label: TextView? = null
        var lastScale: Float = 0f
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int {
            return filteredApps.size
        }

        override fun getItem(position: Int): Any {
            return filteredApps[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            val holder: ViewHolder
            val scale = settings.iconScale
            if (view == null) {
                val itemLayout = LinearLayout(context)
                itemLayout.orientation = VERTICAL
                itemLayout.gravity = Gravity.CENTER

                val icon = ImageView(context)
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                val size = (baseSize * scale).toInt()
                itemLayout.addView(icon, LayoutParams(size, size))

                val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settings, true)
                val label = TextView(context)
                label.setTextColor(adaptiveColor)
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
                label.gravity = Gravity.CENTER
                label.maxLines = 1
                label.ellipsize = TextUtils.TruncateAt.END
                itemLayout.addView(label)

                view = itemLayout
                holder = ViewHolder()
                holder.icon = icon
                holder.label = label
                holder.lastScale = scale
                view.tag = holder
            } else {
                holder = view.tag as ViewHolder
                if (holder.lastScale != scale) {
                    val baseSize = resources.getDimensionPixelSize(R.dimen.grid_icon_size)
                    val size = (baseSize * scale).toInt()
                    val lp = holder.icon!!.layoutParams
                    if (lp.width != size) {
                        lp.width = size
                        lp.height = size
                        holder.icon!!.layoutParams = lp
                    }
                    holder.label!!.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
                    holder.lastScale = scale
                }
                val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settings, true)
                holder.label!!.setTextColor(adaptiveColor)
            }

            val item = filteredApps[position]
            holder.label!!.text = item.label
            holder.icon!!.setImageBitmap(null)
            holder.icon!!.tag = item.packageName
            if (model != null) {
                model!!.loadIcon(item) { bitmap ->
                    if (bitmap != null && item.packageName == holder.icon!!.tag) {
                        holder.icon!!.setImageBitmap(bitmap)
                    }
                }
            }

            return view!!
        }
    }
}
