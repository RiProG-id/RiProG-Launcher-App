package com.riprog.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(this);
        applyThemeMode(settingsManager.getThemeMode());

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
        scrollView.setBackgroundResource(R.drawable.glass_bg);
        scrollView.setVerticalScrollBarEnabled(false);
        rootContainer.addView(scrollView);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(32));
        scrollView.addView(root);

        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(0, 0, 0, dpToPx(32));

        ImageView titleIcon = new ImageView(this);
        titleIcon.setImageResource(R.drawable.ic_settings);
        titleIcon.setColorFilter(getColor(R.color.foreground));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        iconParams.rightMargin = dpToPx(12);
        titleLayout.addView(titleIcon, iconParams);

        TextView title = new TextView(this);
        title.setText(R.string.title_settings);
        title.setTextSize(32);
        title.setTextColor(getColor(R.color.foreground));
        titleLayout.addView(title);
        root.addView(titleLayout);

        addFreeformSetting(root);
        addHideLabelsSetting(root);

        addThemeSetting(root);
        addScaleSetting(root);

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.foreground_dim));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.setMargins(0, dpToPx(24), 0, dpToPx(24));
        root.addView(divider, dividerParams);

        LinearLayout aboutTitleLayout = new LinearLayout(this);
        aboutTitleLayout.setOrientation(LinearLayout.HORIZONTAL);
        aboutTitleLayout.setGravity(Gravity.CENTER_VERTICAL);
        aboutTitleLayout.setPadding(0, 0, 0, dpToPx(16));

        ImageView aboutIcon = new ImageView(this);
        aboutIcon.setImageResource(R.drawable.ic_about);
        aboutIcon.setColorFilter(getColor(R.color.foreground));
        LinearLayout.LayoutParams aboutIconParams = new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24));
        aboutIconParams.rightMargin = dpToPx(8);
        aboutTitleLayout.addView(aboutIcon, aboutIconParams);

        TextView aboutTitle = new TextView(this);
        aboutTitle.setText(R.string.title_about);
        aboutTitle.setTextSize(24);
        aboutTitle.setTextColor(getColor(R.color.foreground));
        aboutTitleLayout.addView(aboutTitle);
        root.addView(aboutTitleLayout);

        TextView aboutContent = new TextView(this);
        aboutContent.setText(R.string.about_content);
        aboutContent.setTextColor(getColor(R.color.foreground_dim));
        aboutContent.setTextSize(14);
        aboutContent.setPadding(0, 0, 0, dpToPx(32));
        Linkify.addLinks(aboutContent, Linkify.WEB_URLS);
        aboutContent.setMovementMethod(LinkMovementMethod.getInstance());
        root.addView(aboutContent);

        setContentView(rootContainer);
    }

    private void addThemeSetting(LinearLayout parent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, dpToPx(16), 0, dpToPx(16));

        TextView titleView = new TextView(this);
        titleView.setText(R.string.setting_theme_mode);
        titleView.setTextSize(18);
        titleView.setTextColor(getColor(R.color.foreground));
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
            option.setTextColor(isSelected ? getColor(R.color.foreground) : getColor(R.color.foreground_dim));

            if (isSelected) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(getColor(R.color.search_background));
                gd.setCornerRadius(dpToPx(8));
                option.setBackground(gd);
            }

            option.setOnClickListener(v -> {
                settingsManager.setThemeMode(values[index]);
                applyThemeMode(values[index]);
                recreate();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            optionsLayout.addView(option, lp);
            option.setGravity(Gravity.CENTER);
        }
        item.addView(optionsLayout);
        parent.addView(item);
    }

    private void applyThemeMode(String mode) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            int nightMode = UiModeManager.MODE_NIGHT_AUTO;
            if ("light".equals(mode)) nightMode = UiModeManager.MODE_NIGHT_NO;
            else if ("dark".equals(mode)) nightMode = UiModeManager.MODE_NIGHT_YES;
            uiModeManager.setApplicationNightMode(nightMode);
        }
    }

    private void applySettingItemStyle(LinearLayout item) {
        item.setClickable(true);
        item.setFocusable(true);

        float radius = dpToPx(12);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        mask.setCornerRadius(radius);

        item.setBackground(new RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.search_background)),
                null,
                mask
        ));
    }

    private void addFreeformSetting(LinearLayout parent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        applySettingItemStyle(item);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        item.addView(textLayout, textParams);

        TextView titleView = new TextView(this);
        titleView.setText(R.string.setting_freeform);
        titleView.setTextSize(18);
        titleView.setTextColor(getColor(R.color.foreground));
        textLayout.addView(titleView);

        TextView summaryView = new TextView(this);
        summaryView.setText(R.string.setting_freeform_summary);
        summaryView.setTextSize(14);
        summaryView.setTextColor(getColor(R.color.foreground_dim));
        textLayout.addView(summaryView);

        Switch toggle = new Switch(this);
        toggle.setChecked(settingsManager.isFreeformHome());
        toggle.setClickable(false);
        item.addView(toggle);

        item.setOnClickListener(v -> {
            boolean newState = !toggle.isChecked();
            toggle.setChecked(newState);
            settingsManager.setFreeformHome(newState);
        });

        parent.addView(item);
    }

    private void addHideLabelsSetting(LinearLayout parent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        applySettingItemStyle(item);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        item.addView(textLayout, textParams);

        TextView titleView = new TextView(this);
        titleView.setText(R.string.setting_hide_labels);
        titleView.setTextSize(18);
        titleView.setTextColor(getColor(R.color.foreground));
        textLayout.addView(titleView);

        TextView summaryView = new TextView(this);
        summaryView.setText(R.string.setting_hide_labels_summary);
        summaryView.setTextSize(14);
        summaryView.setTextColor(getColor(R.color.foreground_dim));
        textLayout.addView(summaryView);

        Switch toggle = new Switch(this);
        toggle.setChecked(settingsManager.isHideLabels());
        toggle.setClickable(false);
        item.addView(toggle);

        item.setOnClickListener(v -> {
            boolean newState = !toggle.isChecked();
            toggle.setChecked(newState);
            settingsManager.setHideLabels(newState);
        });

        parent.addView(item);
    }

    private void addScaleSetting(LinearLayout parent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, dpToPx(16), 0, dpToPx(16));

        TextView titleView = new TextView(this);
        titleView.setText(R.string.setting_scale);
        titleView.setTextSize(18);
        titleView.setTextColor(getColor(R.color.foreground));
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
        description.setTextColor(getColor(R.color.foreground_dim));
        item.addView(description);

        parent.addView(item);
    }

    private void addSettingItem(LinearLayout parent, String title, String summary, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, dpToPx(16), 0, dpToPx(16));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(listener);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTextColor(getColor(R.color.foreground));
        item.addView(titleView);

        TextView summaryView = new TextView(this);
        summaryView.setText(summary);
        summaryView.setTextSize(14);
        summaryView.setTextColor(getColor(R.color.foreground_dim));
        item.addView(summaryView);

        parent.addView(item);
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
