package com.riprog.launcher.ui.activities

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.theme.ThemeManager
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.R

import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

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

        val rootContainer = FrameLayout(this)
        rootContainer.setPadding(dpToPx(16), dpToPx(48), dpToPx(16), dpToPx(32))

        rootContainer.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemInsets = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(dpToPx(16), systemInsets.top + dpToPx(16), dpToPx(16), systemInsets.bottom + dpToPx(16))
            } else {
                v.setPadding(dpToPx(16), insets.systemWindowInsetTop + dpToPx(16), dpToPx(16), insets.systemWindowInsetBottom + dpToPx(16))
            }
            insets
        }

        val scrollView = ScrollView(this)
        scrollView.background = ThemeUtils.getGlassDrawable(this, settingsManager)
        scrollView.isVerticalScrollBarEnabled = false
        rootContainer.addView(scrollView)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(32))
        scrollView.addView(root)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)

        val titleLayout = LinearLayout(this)
        titleLayout.orientation = LinearLayout.HORIZONTAL
        titleLayout.gravity = Gravity.CENTER_VERTICAL
        titleLayout.setPadding(0, 0, 0, dpToPx(32))

        val titleIcon = ImageView(this)
        titleIcon.setImageResource(R.drawable.ic_settings)
        titleIcon.setColorFilter(adaptiveColor)
        val iconParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
        iconParams.rightMargin = dpToPx(12)
        titleLayout.addView(titleIcon, iconParams)

        val title = TextView(this)
        title.setText(R.string.title_settings)
        title.textSize = 32f
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        title.setTextColor(adaptiveColor)
        titleLayout.addView(title)
        root.addView(titleLayout)

        addCategoryHeader(root, getString(R.string.category_home), R.drawable.ic_layout)
        addFreeformSetting(root)
        addHideLabelsSetting(root)

        addCategoryHeader(root, getString(R.string.category_appearance), R.drawable.ic_wallpaper)
        addThemeSetting(root)
        addLiquidGlassSetting(root)
        addDarkenWallpaperSetting(root)
        addScaleSetting(root)

        addCategoryHeader(root, getString(R.string.category_about), R.drawable.ic_info)

        val aboutContent = TextView(this)
        aboutContent.setText(R.string.about_content)
        aboutContent.setTextColor(adaptiveColor and 0xBBFFFFFF.toInt())
        aboutContent.textSize = 14f
        aboutContent.setPadding(0, 0, 0, dpToPx(32))
        Linkify.addLinks(aboutContent, Linkify.WEB_URLS)
        aboutContent.movementMethod = LinkMovementMethod.getInstance()
        root.addView(aboutContent)

        setContentView(rootContainer)
    }

    private fun addCategoryHeader(parent: LinearLayout, title: String, iconRes: Int) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.gravity = Gravity.CENTER_VERTICAL
        layout.setPadding(0, dpToPx(24), 0, dpToPx(12))

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)

        if (iconRes != 0) {
            val icon = ImageView(this)
            icon.setImageResource(iconRes)
            icon.setColorFilter(adaptiveColor)
            val lp = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
            lp.rightMargin = dpToPx(8)
            layout.addView(icon, lp)
        }

        val tv = TextView(this)
        tv.text = title
        tv.textSize = 14f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(adaptiveColor)
        tv.isAllCaps = true
        layout.addView(tv)

        parent.addView(layout)
    }

    private fun addThemeSetting(parent: LinearLayout) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        ThemeManager.applySettingItemStyle(this, item, settingsManager)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)

        val titleView = TextView(this)
        titleView.setText(R.string.setting_theme_mode)
        titleView.textSize = 18f
        titleView.setTextColor(adaptiveColor)
        item.addView(titleView)

        val modes = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val values = arrayOf("system", "light", "dark")
        val current = settingsManager.themeMode

        val optionsLayout = LinearLayout(this)
        optionsLayout.orientation = LinearLayout.HORIZONTAL
        optionsLayout.setPadding(0, dpToPx(8), 0, 0)

        for (i in modes.indices) {
            val index = i
            val option = TextView(this)
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
                ThemeManager.applyThemeMode(this, values[index])
                recreate()
            }

            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            optionsLayout.addView(option, lp)
            option.gravity = Gravity.CENTER
        }
        item.addView(optionsLayout)

        val itemLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        itemLp.bottomMargin = dpToPx(8)
        parent.addView(item, itemLp)
    }

    private fun addFreeformSetting(parent: LinearLayout) {
        addToggleSetting(parent, R.string.setting_freeform, R.string.setting_freeform_summary,
            settingsManager.isFreeformHome) { isChecked -> settingsManager.isFreeformHome = isChecked }
    }

    private fun addHideLabelsSetting(parent: LinearLayout) {
        addToggleSetting(parent, R.string.setting_hide_labels, R.string.setting_hide_labels_summary,
            settingsManager.isHideLabels) { isChecked -> settingsManager.isHideLabels = isChecked }
    }

    private fun addLiquidGlassSetting(parent: LinearLayout) {
        addToggleSetting(parent, R.string.setting_liquid_glass, R.string.setting_liquid_glass_summary,
            settingsManager.isLiquidGlass) { isChecked ->
            settingsManager.isLiquidGlass = isChecked
            recreate()
        }
    }

    private fun addDarkenWallpaperSetting(parent: LinearLayout) {
        addToggleSetting(parent, R.string.setting_darken_wallpaper, R.string.setting_darken_wallpaper_summary,
            settingsManager.isDarkenWallpaper) { isChecked -> settingsManager.isDarkenWallpaper = isChecked }
    }

    private fun addToggleSetting(parent: LinearLayout, titleRes: Int, summaryRes: Int, isChecked: Boolean, listener: (Boolean) -> Unit) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.HORIZONTAL
        item.gravity = Gravity.CENTER_VERTICAL
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        ThemeManager.applySettingItemStyle(this, item, settingsManager)

        val textLayout = LinearLayout(this)
        textLayout.orientation = LinearLayout.VERTICAL
        val textParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        item.addView(textLayout, textParams)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)

        val titleView = TextView(this)
        titleView.setText(titleRes)
        titleView.textSize = 18f
        titleView.setTextColor(adaptiveColor)
        textLayout.addView(titleView)

        val summaryView = TextView(this)
        summaryView.setText(summaryRes)
        summaryView.textSize = 14f
        summaryView.setTextColor(adaptiveColor and 0xBBFFFFFF.toInt())
        textLayout.addView(summaryView)

        val toggle = Switch(this)
        toggle.isChecked = isChecked
        toggle.isClickable = false
        item.addView(toggle)

        item.setOnClickListener {
            val newState = !toggle.isChecked
            toggle.isChecked = newState
            listener(newState)
        }

        val itemLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        itemLp.bottomMargin = dpToPx(8)
        parent.addView(item, itemLp)
    }

    private fun addScaleSetting(parent: LinearLayout) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        ThemeManager.applySettingItemStyle(this, item, settingsManager)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)

        val titleView = TextView(this)
        titleView.setText(R.string.setting_scale)
        titleView.textSize = 18f
        titleView.setTextColor(adaptiveColor)
        item.addView(titleView)

        val seekBar = SeekBar(this)
        seekBar.max = 100
        val currentScale = settingsManager.iconScale
        seekBar.progress = ((currentScale - 0.5f) / 1.0f * 100).toInt()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val scale = 0.5f + (progress / 100.0f) * 1.0f
                settingsManager.iconScale = scale
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        item.addView(seekBar)

        val description = TextView(this)
        description.setText(R.string.setting_scale_summary)
        description.textSize = 12f
        description.setTextColor(adaptiveColor and 0xBBFFFFFF.toInt())
        item.addView(description)

        val itemLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        itemLp.bottomMargin = dpToPx(8)
        parent.addView(item, itemLp)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
