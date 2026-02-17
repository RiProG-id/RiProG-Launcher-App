package com.riprog.launcher.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.app.UiModeManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.riprog.launcher.R
import com.riprog.launcher.ui.home.HomeViewModel
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import com.riprog.launcher.ui.common.ThemeUtils
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModel()
    private val settingsManager: LauncherPreferences by inject()

    override fun onResume() {
        super.onResume()
        if (window != null && window.decorView.isAttachedToWindow) {
            ThemeUtils.updateStatusBarContrast(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.settings.themeMode.collectLatest { mode ->
                applyThemeMode(mode)
            }
        }

        val rootContainer = FrameLayout(this)
        val scrollView = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        rootContainer.addView(scrollView)
        scrollView.addView(root)
        setContentView(rootContainer)

        lifecycleScope.launch {
            val isLiquidGlass = viewModel.settings.isLiquidGlass.first()
            val isDarkenWallpaper = viewModel.settings.isDarkenWallpaper.first()
            val isFreeform = viewModel.settings.isFreeformHome.first()
            val isHideLabels = viewModel.settings.isHideLabels.first()
            val themeMode = viewModel.settings.themeMode.first()

            val currentWindow = window
            if (isLiquidGlass && currentWindow != null) {
                ThemeUtils.applyWindowBlur(currentWindow, true)
            }
            rootContainer.background = ThemeUtils.getGlassDrawable(this@SettingsActivity, settingsManager, 0f)

            val adaptiveColor = ThemeUtils.getAdaptiveColor(this@SettingsActivity, settingsManager, true)

            val closeBtn = ImageView(this@SettingsActivity).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(adaptiveColor)
                alpha = 0.6f
                setOnClickListener { finish() }
            }
            val closeLp = FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.TOP or Gravity.END).apply {
                topMargin = dpToPx(16)
                marginEnd = dpToPx(16)
            }
            rootContainer.addView(closeBtn, closeLp)

            ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                root.setPadding(dpToPx(24), bars.top + dpToPx(64), dpToPx(24), bars.bottom + dpToPx(24))
                closeBtn.layoutParams = (closeBtn.layoutParams as FrameLayout.LayoutParams).apply { topMargin = bars.top + dpToPx(16) }
                insets
            }

            val titleLayout = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(24))
            }
            titleLayout.addView(ImageView(this@SettingsActivity).apply {
                setImageResource(R.drawable.ic_settings)
                setColorFilter(adaptiveColor)
            }, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply { marginEnd = dpToPx(12) })

            titleLayout.addView(TextView(this@SettingsActivity).apply {
                setText(R.string.title_settings)
                textSize = 32f
                setTypeface(null, Typeface.BOLD)
                setTextColor(adaptiveColor)
            })
            root.addView(titleLayout)

            addCategoryHeader(root, getString(R.string.category_appearance), R.drawable.ic_wallpaper)
            addThemeSetting(root, themeMode)
            addToggleSetting(root, R.string.setting_liquid_glass, R.string.setting_liquid_glass_summary,
                isLiquidGlass) { isChecked ->
                lifecycleScope.launch {
                    viewModel.settings.setLiquidGlass(isChecked)
                    recreate()
                }
            }
            addToggleSetting(root, R.string.setting_darken_wallpaper, R.string.setting_darken_wallpaper_summary,
                isDarkenWallpaper) { isChecked -> lifecycleScope.launch { viewModel.settings.setDarkenWallpaper(isChecked) } }

            val initialScale = viewModel.settings.iconScale.first()
            addScaleSetting(root, initialScale)

            addCategoryHeader(root, getString(R.string.category_home), R.drawable.ic_layout)
            addToggleSetting(root, R.string.setting_freeform, R.string.setting_freeform_summary,
                isFreeform) { isChecked -> lifecycleScope.launch { viewModel.settings.setFreeformHome(isChecked) } }
            addToggleSetting(root, R.string.setting_hide_labels, R.string.setting_hide_labels_summary,
                isHideLabels) { isChecked -> lifecycleScope.launch { viewModel.settings.setHideLabels(isChecked) } }

            addCategoryHeader(root, getString(R.string.category_about), R.drawable.ic_info)
            root.addView(TextView(this@SettingsActivity).apply {
                setText(R.string.about_content)
                setTextColor(adaptiveColor)
                textSize = 14f
                setPadding(dpToPx(16), 0, dpToPx(16), dpToPx(32))
                Linkify.addLinks(this, Linkify.WEB_URLS)
                movementMethod = LinkMovementMethod.getInstance()
            })
        }
    }

    private fun addCategoryHeader(parent: LinearLayout, title: String, iconRes: Int) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.gravity = Gravity.CENTER_VERTICAL
        layout.setPadding(dpToPx(8), dpToPx(32), 0, dpToPx(16))

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)

        if (iconRes != 0) {
            val icon = ImageView(this)
            icon.setImageResource(iconRes)
            icon.setColorFilter(adaptiveColor)
            val lp = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
            lp.rightMargin = dpToPx(12)
            layout.addView(icon, lp)
        }

        val tv = TextView(this)
        tv.text = title
        tv.textSize = 20f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(adaptiveColor)
        layout.addView(tv)

        parent.addView(layout)
    }

    private fun addToggleSetting(parent: LinearLayout, titleRes: Int, summaryRes: Int, isChecked: Boolean, listener: (Boolean) -> Unit) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.HORIZONTAL
        item.gravity = Gravity.CENTER_VERTICAL
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        applySettingItemStyle(item)

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

    private fun addThemeSetting(parent: LinearLayout, current: String) {
        val item = LinearLayout(this@SettingsActivity)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        applySettingItemStyle(item)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this@SettingsActivity, settingsManager, true)

        val titleView = TextView(this@SettingsActivity)
        titleView.setText(R.string.setting_theme_mode)
        titleView.textSize = 18f
        titleView.setTextColor(adaptiveColor)
        item.addView(titleView)

        val modes = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val values = arrayOf("system", "light", "dark")

        val optionsLayout = LinearLayout(this@SettingsActivity)
        optionsLayout.orientation = LinearLayout.HORIZONTAL
        optionsLayout.setPadding(0, dpToPx(8), 0, 0)

        for (i in modes.indices) {
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
                lifecycleScope.launch {
                    viewModel.settings.setThemeMode(values[i])
                    applyThemeMode(values[i])
                    recreate()
                }
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

    private fun applyThemeMode(mode: String?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val nightMode = when (mode) {
                "light" -> UiModeManager.MODE_NIGHT_NO
                "dark" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            if (uiModeManager.nightMode != nightMode) {
                uiModeManager.setApplicationNightMode(nightMode)
            }
        }
    }

    private fun applySettingItemStyle(item: LinearLayout) {
        item.isClickable = true
        item.isFocusable = true

        val radius = dpToPx(12).toFloat()
        val mask = GradientDrawable()
        mask.setColor(Color.BLACK)
        mask.cornerRadius = radius

        item.background = RippleDrawable(
            ColorStateList.valueOf(getColor(R.color.search_background)),
            null,
            mask
        )
    }

    private fun addScaleSetting(parent: LinearLayout, currentScale: Float) {
        val item = LinearLayout(this@SettingsActivity)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        applySettingItemStyle(item)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(this@SettingsActivity, settingsManager, true)

        val titleView = TextView(this@SettingsActivity)
        titleView.setText(R.string.setting_scale)
        titleView.textSize = 18f
        titleView.setTextColor(adaptiveColor)
        item.addView(titleView)

        val seekBar = SeekBar(this@SettingsActivity)
        seekBar.max = 100
        seekBar.progress = ((currentScale - 0.5f) / 1.0f * 100).toInt()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val scale = 0.5f + (progress / 100.0f) * 1.0f
                lifecycleScope.launch {
                    viewModel.settings.setIconScale(scale)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        item.addView(seekBar)

        val description = TextView(this@SettingsActivity).apply {
            setText(R.string.setting_scale_summary)
            textSize = 12f
            setTextColor(adaptiveColor and 0xBBFFFFFF.toInt())
        }
        item.addView(description)

        val itemLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        itemLp.bottomMargin = dpToPx(8)
        parent.addView(item, itemLp)
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
