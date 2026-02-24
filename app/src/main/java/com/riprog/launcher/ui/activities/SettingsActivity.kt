package com.riprog.launcher.ui.activities

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.theme.ThemeManager
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.R

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : Activity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var recyclerView: RecyclerView

    override fun attachBaseContext(newBase: Context) {
        val sm = SettingsManager(newBase)
        super.attachBaseContext(ThemeManager.applyThemeToContext(newBase, sm.themeMode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        ThemeManager.applyThemeMode(this, settingsManager.themeMode)

        val w = window
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(w, false)
        ThemeUtils.applyWindowBlur(w, settingsManager.isLiquidGlass)

        val rootContainer = FrameLayout(this)
        rootContainer.background = ThemeUtils.getGlassDrawable(this, settingsManager, 0f)

        recyclerView = RecyclerView(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.isVerticalScrollBarEnabled = false
        recyclerView.clipToPadding = false

        // Initial padding
        recyclerView.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(32))

        rootContainer.addView(recyclerView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val closeBtn = ImageView(this)
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)
        closeBtn.setColorFilter(adaptiveColor)
        closeBtn.alpha = 0.6f
        closeBtn.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        closeBtn.setOnClickListener { finish() }
        val closeLp = FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.TOP or Gravity.END)
        rootContainer.addView(closeBtn, closeLp)

        ViewCompat.setOnApplyWindowInsetsListener(closeBtn) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = systemBars.top + dpToPx(16)
            lp.rightMargin = dpToPx(16)
            v.layoutParams = lp
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                dpToPx(24),
                systemBars.top + dpToPx(32),
                dpToPx(24),
                systemBars.bottom + dpToPx(32)
            )
            insets
        }

        setupAdapter()
        setContentView(rootContainer)
    }

    private fun setupAdapter() {
        val items = mutableListOf<SettingItem>()
        items.add(SettingItem(SettingType.TITLE))

        items.add(SettingItem(SettingType.CATEGORY, titleString = getString(R.string.category_home), iconRes = R.drawable.ic_layout))
        items.add(SettingItem(SettingType.TOGGLE, titleRes = R.string.setting_freeform, summaryRes = R.string.setting_freeform_summary, isChecked = settingsManager.isFreeformHome) {
            settingsManager.isFreeformHome = it
        })
        items.add(SettingItem(SettingType.TOGGLE, titleRes = R.string.setting_hide_labels, summaryRes = R.string.setting_hide_labels_summary, isChecked = settingsManager.isHideLabels) {
            settingsManager.isHideLabels = it
        })

        items.add(SettingItem(SettingType.CATEGORY, titleString = getString(R.string.category_appearance), iconRes = R.drawable.ic_wallpaper))
        items.add(SettingItem(SettingType.THEME))
        items.add(SettingItem(SettingType.TOGGLE, titleRes = R.string.setting_liquid_glass, summaryRes = R.string.setting_liquid_glass_summary, isChecked = settingsManager.isLiquidGlass) {
            settingsManager.isLiquidGlass = it
            recreate()
        })
        items.add(SettingItem(SettingType.TOGGLE, titleRes = R.string.setting_darken_wallpaper, summaryRes = R.string.setting_darken_wallpaper_summary, isChecked = settingsManager.isDarkenWallpaper) {
            settingsManager.isDarkenWallpaper = it
        })

        items.add(SettingItem(SettingType.CATEGORY, titleString = getString(R.string.category_about), iconRes = R.drawable.ic_info))
        items.add(SettingItem(SettingType.ABOUT))

        recyclerView.adapter = SettingsAdapter(items)
    }

    private enum class SettingType {
        TITLE, CATEGORY, TOGGLE, THEME, ABOUT
    }

    private data class SettingItem(
        val type: SettingType,
        val titleRes: Int = 0,
        val summaryRes: Int = 0,
        val iconRes: Int = 0,
        val titleString: String? = null,
        var isChecked: Boolean = false,
        val onToggle: ((Boolean) -> Unit)? = null
    )

    private inner class SettingsAdapter(val items: List<SettingItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int = items[position].type.ordinal
        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val context = parent.context
            val type = SettingType.values()[viewType]
            val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)

            return when (type) {
                SettingType.TITLE -> {
                    val titleLayout = LinearLayout(context)
                    titleLayout.orientation = LinearLayout.HORIZONTAL
                    titleLayout.gravity = Gravity.CENTER_VERTICAL
                    titleLayout.setPadding(0, 0, 0, dpToPx(32))

                    val titleIcon = ImageView(context)
                    titleIcon.setImageResource(R.drawable.ic_settings)
                    titleIcon.setColorFilter(adaptiveColor)
                    val iconParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
                    iconParams.rightMargin = dpToPx(12)
                    titleLayout.addView(titleIcon, iconParams)

                    val title = TextView(context)
                    title.setText(R.string.title_settings)
                    title.textSize = 32f
                    title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    title.setTextColor(adaptiveColor)
                    titleLayout.addView(title)
                    SimpleViewHolder(titleLayout)
                }
                SettingType.CATEGORY -> {
                    val layout = LinearLayout(context)
                    layout.orientation = LinearLayout.HORIZONTAL
                    layout.gravity = Gravity.CENTER_VERTICAL
                    layout.setPadding(0, dpToPx(24), 0, dpToPx(12))
                    SimpleViewHolder(layout)
                }
                SettingType.TOGGLE -> {
                    val item = LinearLayout(context)
                    item.orientation = LinearLayout.HORIZONTAL
                    item.gravity = Gravity.CENTER_VERTICAL
                    item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                    ThemeManager.applySettingItemStyle(context as Activity, item, settingsManager)

                    val textLayout = LinearLayout(context)
                    textLayout.orientation = LinearLayout.VERTICAL
                    val textParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    item.addView(textLayout, textParams)

                    val titleView = TextView(context)
                    titleView.textSize = 18f
                    titleView.setTextColor(adaptiveColor)
                    textLayout.addView(titleView)

                    val summaryView = TextView(context)
                    summaryView.textSize = 14f
                    summaryView.setTextColor(adaptiveColor and 0xBBFFFFFF.toInt())
                    textLayout.addView(summaryView)

                    val toggle = Switch(context)
                    toggle.isClickable = false
                    item.addView(toggle)

                    val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dpToPx(8)
                    item.layoutParams = lp
                    ToggleViewHolder(item, titleView, summaryView, toggle)
                }
                SettingType.THEME -> {
                    val item = LinearLayout(context)
                    item.orientation = LinearLayout.VERTICAL
                    item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                    ThemeManager.applySettingItemStyle(context as Activity, item, settingsManager)

                    val titleView = TextView(context)
                    titleView.setText(R.string.setting_theme_mode)
                    titleView.textSize = 18f
                    titleView.setTextColor(adaptiveColor)
                    item.addView(titleView)

                    val optionsLayout = LinearLayout(context)
                    optionsLayout.orientation = LinearLayout.HORIZONTAL
                    optionsLayout.setPadding(0, dpToPx(8), 0, 0)
                    item.addView(optionsLayout)

                    val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dpToPx(8)
                    item.layoutParams = lp
                    ThemeViewHolder(item, optionsLayout)
                }
                SettingType.ABOUT -> {
                    val aboutContent = TextView(context)
                    aboutContent.setTextColor(adaptiveColor and 0xBBFFFFFF.toInt())
                    aboutContent.textSize = 14f
                    aboutContent.setPadding(0, 0, 0, dpToPx(32))
                    SimpleViewHolder(aboutContent)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val adaptiveColor = ThemeUtils.getAdaptiveColor(this@SettingsActivity, settingsManager, true)
            when (item.type) {
                SettingType.CATEGORY -> {
                    val layout = holder.itemView as LinearLayout
                    layout.removeAllViews()
                    if (item.iconRes != 0) {
                        val icon = ImageView(layout.context)
                        icon.setImageResource(item.iconRes)
                        icon.setColorFilter(adaptiveColor)
                        val lp = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
                        lp.rightMargin = dpToPx(8)
                        layout.addView(icon, lp)
                    }
                    val tv = TextView(layout.context)
                    tv.text = item.titleString
                    tv.textSize = 14f
                    tv.setTypeface(null, Typeface.BOLD)
                    tv.setTextColor(adaptiveColor)
                    tv.isAllCaps = true
                    layout.addView(tv)
                }
                SettingType.TOGGLE -> {
                    val h = holder as ToggleViewHolder
                    h.title.setText(item.titleRes)
                    h.summary.setText(item.summaryRes)
                    h.toggle.isChecked = item.isChecked
                    h.itemView.setOnClickListener {
                        val newState = !h.toggle.isChecked
                        h.toggle.isChecked = newState
                        item.isChecked = newState
                        item.onToggle?.invoke(newState)
                    }
                }
                SettingType.THEME -> {
                    val h = holder as ThemeViewHolder
                    h.options.removeAllViews()
                    val modes = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
                    val values = arrayOf("system", "light", "dark")
                    val current = settingsManager.themeMode

                    for (i in modes.indices) {
                        val index = i
                        val option = TextView(this@SettingsActivity)
                        option.text = modes[i]
                        option.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                        option.textSize = 14f

                        val isSelected = values[i] == current
                        option.setTextColor(if (isSelected) adaptiveColor else adaptiveColor and 0xBBFFFFFF.toInt())

                        if (isSelected) {
                            val gd = GradientDrawable()
                            gd.setColor(getColor(R.color.search_background))
                            gd.cornerRadius = dpToPx(8).toFloat()
                            option.background = gd
                        }

                        option.setOnClickListener {
                            settingsManager.themeMode = values[index]
                            val changed = ThemeManager.applyThemeMode(this@SettingsActivity, values[index])
                            if (!changed) {
                                recreate()
                            }
                        }

                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        h.options.addView(option, lp)
                        option.gravity = Gravity.CENTER
                    }
                }
                SettingType.ABOUT -> {
                    val tv = holder.itemView as TextView
                    tv.setText(R.string.about_content)
                    Linkify.addLinks(tv, Linkify.WEB_URLS)
                    tv.movementMethod = LinkMovementMethod.getInstance()
                }
                else -> {}
            }
        }
    }

    private class SimpleViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private class ToggleViewHolder(view: View, val title: TextView, val summary: TextView, val toggle: Switch) : RecyclerView.ViewHolder(view)
    private class ThemeViewHolder(view: View, val options: LinearLayout) : RecyclerView.ViewHolder(view)

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
