package com.riprog.launcher

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        applyThemeMode(settingsManager.themeMode)

        val w = window
        @Suppress("DEPRECATION")
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        @Suppress("DEPRECATION")
        w.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        w.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            w.setDecorFitsSystemWindows(false)
        }

        val rootContainer = FrameLayout(this)
        rootContainer.setPadding(dpToPx(16), dpToPx(48), dpToPx(16), dpToPx(32))

        rootContainer.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemInsets = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(dpToPx(16), systemInsets.top + dpToPx(16), dpToPx(16), systemInsets.bottom + dpToPx(16))
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(dpToPx(16), insets.systemWindowInsetTop + dpToPx(16), dpToPx(16), insets.systemWindowInsetBottom + dpToPx(16))
            }
            insets
        }

        val scrollView = ScrollView(this)
        scrollView.setBackgroundResource(R.drawable.glass_bg)
        scrollView.isVerticalScrollBarEnabled = false
        rootContainer.addView(scrollView)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(32))
        scrollView.addView(root)

        val titleLayout = LinearLayout(this)
        titleLayout.orientation = LinearLayout.HORIZONTAL
        titleLayout.gravity = Gravity.CENTER_VERTICAL
        titleLayout.setPadding(0, 0, 0, dpToPx(32))

        val titleIcon = ImageView(this)
        titleIcon.setImageResource(R.drawable.ic_settings)
        titleIcon.setColorFilter(getColor(R.color.foreground))
        val iconParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
        iconParams.rightMargin = dpToPx(12)
        titleLayout.addView(titleIcon, iconParams)

        val title = TextView(this)
        title.setText(R.string.title_settings)
        title.textSize = 32f
        title.setTextColor(getColor(R.color.foreground))
        titleLayout.addView(title)
        root.addView(titleLayout)

        addFreeformSetting(root)
        addThemeSetting(root)
        addScaleSetting(root)

        val divider = View(this)
        divider.setBackgroundColor(getColor(R.color.foreground_dim))
        val dividerParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        dividerParams.setMargins(0, dpToPx(24), 0, dpToPx(24))
        root.addView(divider, dividerParams)

        val aboutTitleLayout = LinearLayout(this)
        aboutTitleLayout.orientation = LinearLayout.HORIZONTAL
        aboutTitleLayout.gravity = Gravity.CENTER_VERTICAL
        aboutTitleLayout.setPadding(0, 0, 0, dpToPx(16))

        val aboutIcon = ImageView(this)
        aboutIcon.setImageResource(R.drawable.ic_about)
        aboutIcon.setColorFilter(getColor(R.color.foreground))
        val aboutIconParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
        aboutIconParams.rightMargin = dpToPx(8)
        aboutTitleLayout.addView(aboutIcon, aboutIconParams)

        val aboutTitle = TextView(this)
        aboutTitle.setText(R.string.title_about)
        aboutTitle.textSize = 24f
        aboutTitle.setTextColor(getColor(R.color.foreground))
        aboutTitleLayout.addView(aboutTitle)
        root.addView(aboutTitleLayout)

        val aboutContent = TextView(this)
        aboutContent.setText(R.string.about_content)
        aboutContent.setTextColor(getColor(R.color.foreground_dim))
        aboutContent.textSize = 14f
        aboutContent.setPadding(0, 0, 0, dpToPx(32))
        Linkify.addLinks(aboutContent, Linkify.WEB_URLS)
        aboutContent.movementMethod = LinkMovementMethod.getInstance()
        root.addView(aboutContent)

        setContentView(rootContainer)
    }

    private fun addThemeSetting(parent: LinearLayout) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(0, dpToPx(16), 0, dpToPx(16))

        val titleView = TextView(this)
        titleView.setText(R.string.setting_theme_mode)
        titleView.textSize = 18f
        titleView.setTextColor(getColor(R.color.foreground))
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
            option.setTextColor(if (isSelected) getColor(R.color.foreground) else getColor(R.color.foreground_dim))

            if (isSelected) {
                val gd = GradientDrawable()
                gd.setColor(getColor(R.color.search_background))
                gd.cornerRadius = dpToPx(8).toFloat()
                option.background = gd
            }

            option.setOnClickListener {
                settingsManager.themeMode = values[index]
                applyThemeMode(values[index])
                recreate()
            }

            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            optionsLayout.addView(option, lp)
            option.gravity = Gravity.CENTER
        }
        item.addView(optionsLayout)
        parent.addView(item)
    }

    private fun applyThemeMode(mode: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            var nightMode = UiModeManager.MODE_NIGHT_AUTO
            if ("light" == mode) nightMode = UiModeManager.MODE_NIGHT_NO else if ("dark" == mode) nightMode = UiModeManager.MODE_NIGHT_YES
            uiModeManager.setApplicationNightMode(nightMode)
        }
    }

    private fun addFreeformSetting(parent: LinearLayout) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.HORIZONTAL
        item.gravity = Gravity.CENTER_VERTICAL
        item.setPadding(0, dpToPx(16), 0, dpToPx(16))

        val textLayout = LinearLayout(this)
        textLayout.orientation = LinearLayout.VERTICAL
        val textParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        item.addView(textLayout, textParams)

        val titleView = TextView(this)
        titleView.setText(R.string.setting_freeform)
        titleView.textSize = 18f
        titleView.setTextColor(getColor(R.color.foreground))
        textLayout.addView(titleView)

        val summaryView = TextView(this)
        summaryView.setText(R.string.setting_freeform_summary)
        summaryView.textSize = 14f
        summaryView.setTextColor(getColor(R.color.foreground_dim))
        textLayout.addView(summaryView)

        val toggle = Switch(this)
        toggle.isChecked = settingsManager.isFreeformHome
        toggle.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isFreeformHome = isChecked
        }
        item.addView(toggle)

        parent.addView(item)
    }

    private fun addScaleSetting(parent: LinearLayout) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(0, dpToPx(16), 0, dpToPx(16))

        val titleView = TextView(this)
        titleView.setText(R.string.setting_scale)
        titleView.textSize = 18f
        titleView.setTextColor(getColor(R.color.foreground))
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
        description.setTextColor(getColor(R.color.foreground_dim))
        item.addView(description)

        parent.addView(item)
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
