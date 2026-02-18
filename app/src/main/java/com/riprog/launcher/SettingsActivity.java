package com.riprog.launcher;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private SettingsManager settingsManager;

    @Override
    protected void attachBaseContext(Context newBase) {
        SettingsManager sm = new SettingsManager(newBase);
        super.attachBaseContext(ThemeMechanism.applyThemeToContext(newBase, sm.getThemeMode()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(this);
        ThemeMechanism.applyThemeMode(this, settingsManager.getThemeMode());

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        }

        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setPadding(dpToPx(16), dpToPx(48), dpToPx(16), dpToPx(32));

        rootContainer.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Insets systemInsets = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(dpToPx(16), systemInsets.top + dpToPx(16), dpToPx(16), systemInsets.bottom + dpToPx(16));
            } else {
                v.setPadding(dpToPx(16), insets.getSystemWindowInsetTop() + dpToPx(16), dpToPx(16), insets.getSystemWindowInsetBottom() + dpToPx(16));
            }
            return insets;
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackground(ThemeUtils.getGlassDrawable(this, settingsManager));
        scrollView.setVerticalScrollBarEnabled(false);
        rootContainer.addView(scrollView);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(32));
        scrollView.addView(root);

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);

        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(0, 0, 0, dpToPx(32));

        ImageView titleIcon = new ImageView(this);
        titleIcon.setImageResource(R.drawable.ic_settings);
        titleIcon.setColorFilter(adaptiveColor);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        iconParams.rightMargin = dpToPx(12);
        titleLayout.addView(titleIcon, iconParams);

        TextView title = new TextView(this);
        title.setText(R.string.title_settings);
        title.setTextSize(32);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setTextColor(adaptiveColor);
        titleLayout.addView(title);
        root.addView(titleLayout);

        addCategoryHeader(root, getString(R.string.category_home), R.drawable.ic_layout);
        addFreeformSetting(root);
        addHideLabelsSetting(root);

        addCategoryHeader(root, getString(R.string.category_appearance), R.drawable.ic_wallpaper);
        addThemeSetting(root);
        addLiquidGlassSetting(root);
        addDarkenWallpaperSetting(root);
        addScaleSetting(root);

        addCategoryHeader(root, getString(R.string.category_about), R.drawable.ic_info);

        TextView aboutContent = new TextView(this);
        aboutContent.setText(R.string.about_content);
        aboutContent.setTextColor(adaptiveColor & 0xBBFFFFFF);
        aboutContent.setTextSize(14);
        aboutContent.setPadding(0, 0, 0, dpToPx(32));
        Linkify.addLinks(aboutContent, Linkify.WEB_URLS);
        aboutContent.setMovementMethod(LinkMovementMethod.getInstance());
        root.addView(aboutContent);

        setContentView(rootContainer);
    }

    private void addCategoryHeader(LinearLayout parent, String title, int iconRes) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(0, dpToPx(24), 0, dpToPx(12));

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);

        if (iconRes != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            icon.setColorFilter(adaptiveColor);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
            lp.rightMargin = dpToPx(8);
            layout.addView(icon, lp);
        }

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(14);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(adaptiveColor);
        tv.setAllCaps(true);
        layout.addView(tv);

        parent.addView(layout);
    }

    private void addThemeSetting(LinearLayout parent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        ThemeMechanism.applySettingItemStyle(this, item, settingsManager);

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);

        TextView titleView = new TextView(this);
        titleView.setText(R.string.setting_theme_mode);
        titleView.setTextSize(18);
        titleView.setTextColor(adaptiveColor);
        item.addView(titleView);

        String[] modes = {getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
        String[] values = {"system", "light", "dark"};
        String current = settingsManager.getThemeMode();

        LinearLayout optionsLayout = new LinearLayout(this);
        optionsLayout.setOrientation(LinearLayout.HORIZONTAL);
        optionsLayout.setPadding(0, dpToPx(8), 0, 0);

        for (int i = 0; i < modes.length; i++) {
            final int index = i;
            TextView option = new TextView(this);
            option.setText(modes[i]);
            option.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            option.setTextSize(14);

            boolean isSelected = values[i].equals(current);
            option.setTextColor(isSelected ? adaptiveColor : adaptiveColor & 0xBBFFFFFF);

            if (isSelected) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(getColor(R.color.search_background));
                gd.setCornerRadius(dpToPx(8));
                option.setBackground(gd);
            }

            option.setOnClickListener(v -> {
                settingsManager.setThemeMode(values[index]);
                ThemeMechanism.applyThemeMode(this, values[index]);
                recreate();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            optionsLayout.addView(option, lp);
            option.setGravity(Gravity.CENTER);
        }
        item.addView(optionsLayout);

        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemLp.bottomMargin = dpToPx(8);
        parent.addView(item, itemLp);
    }

    private void addFreeformSetting(LinearLayout parent) {
        addToggleSetting(parent, R.string.setting_freeform, R.string.setting_freeform_summary,
                settingsManager.isFreeformHome(), (isChecked) -> settingsManager.setFreeformHome(isChecked));
    }

    private void addHideLabelsSetting(LinearLayout parent) {
        addToggleSetting(parent, R.string.setting_hide_labels, R.string.setting_hide_labels_summary,
                settingsManager.isHideLabels(), (isChecked) -> settingsManager.setHideLabels(isChecked));
    }

    private void addLiquidGlassSetting(LinearLayout parent) {
        addToggleSetting(parent, R.string.setting_liquid_glass, R.string.setting_liquid_glass_summary,
                settingsManager.isLiquidGlass(), (isChecked) -> {
                    settingsManager.setLiquidGlass(isChecked);
                    recreate();
                });
    }

    private void addDarkenWallpaperSetting(LinearLayout parent) {
        addToggleSetting(parent, R.string.setting_darken_wallpaper, R.string.setting_darken_wallpaper_summary,
                settingsManager.isDarkenWallpaper(), (isChecked) -> settingsManager.setDarkenWallpaper(isChecked));
    }

    private void addToggleSetting(LinearLayout parent, int titleRes, int summaryRes, boolean isChecked, OnCheckedChangeListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        ThemeMechanism.applySettingItemStyle(this, item, settingsManager);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        item.addView(textLayout, textParams);

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);

        TextView titleView = new TextView(this);
        titleView.setText(titleRes);
        titleView.setTextSize(18);
        titleView.setTextColor(adaptiveColor);
        textLayout.addView(titleView);

        TextView summaryView = new TextView(this);
        summaryView.setText(summaryRes);
        summaryView.setTextSize(14);
        summaryView.setTextColor(adaptiveColor & 0xBBFFFFFF);
        textLayout.addView(summaryView);

        Switch toggle = new Switch(this);
        toggle.setChecked(isChecked);
        toggle.setClickable(false);
        item.addView(toggle);

        item.setOnClickListener(v -> {
            boolean newState = !toggle.isChecked();
            toggle.setChecked(newState);
            listener.onCheckedChanged(newState);
        });

        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemLp.bottomMargin = dpToPx(8);
        parent.addView(item, itemLp);
    }

    private void addScaleSetting(LinearLayout parent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        ThemeMechanism.applySettingItemStyle(this, item, settingsManager);

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);

        TextView titleView = new TextView(this);
        titleView.setText(R.string.setting_scale);
        titleView.setTextSize(18);
        titleView.setTextColor(adaptiveColor);
        item.addView(titleView);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        float currentScale = settingsManager.getIconScale();
        seekBar.setProgress((int) ((currentScale - 0.5f) / 1.0f * 100));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float scale = 0.5f + (progress / 100.0f) * 1.0f;
                settingsManager.setIconScale(scale);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        item.addView(seekBar);

        TextView description = new TextView(this);
        description.setText(R.string.setting_scale_summary);
        description.setTextSize(12);
        description.setTextColor(adaptiveColor & 0xBBFFFFFF);
        item.addView(description);

        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemLp.bottomMargin = dpToPx(8);
        parent.addView(item, itemLp);
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private interface OnCheckedChangeListener {
        void onCheckedChanged(boolean isChecked);
    }
}
