package com.riprog.launcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WidgetPicker {
    private final MainActivity activity;
    private final SettingsManager settingsManager;
    private final AppWidgetManager appWidgetManager;
    private final android.appwidget.AppWidgetHost appWidgetHost;
    private final ExecutorService widgetPreviewExecutor = Executors.newFixedThreadPool(4);

    public WidgetPicker(MainActivity activity, SettingsManager settingsManager, AppWidgetManager appWidgetManager, android.appwidget.AppWidgetHost appWidgetHost) {
        this.activity = activity;
        this.settingsManager = settingsManager;
        this.appWidgetManager = appWidgetManager;
        this.appWidgetHost = appWidgetHost;
    }

    public void pickWidget() {
        if (appWidgetManager == null) return;
        List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
        if (providers == null) return;

        Map<String, List<AppWidgetProviderInfo>> grouped = new HashMap<>();
        for (AppWidgetProviderInfo info : providers) {
            String pkg = info.provider.getPackageName();
            if (!grouped.containsKey(pkg)) grouped.put(pkg, new ArrayList<>());
            grouped.get(pkg).add(info);
        }

        List<String> packages = new ArrayList<>(grouped.keySet());
        Collections.sort(packages, (a, b) -> activity.getAppName(a).compareToIgnoreCase(activity.getAppName(b)));

        Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }

        FrameLayout root = new FrameLayout(activity);
        root.setBackground(ThemeUtils.getGlassDrawable(activity, settingsManager, 0f));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        root.addView(container);

        int adaptiveColor = ThemeUtils.getAdaptiveColor(activity, settingsManager, true);
        int secondaryColor = (adaptiveColor & 0x00FFFFFF) | 0x80000000;

        LinearLayout titleLayout = new LinearLayout(activity);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(ThemeUtils.dpToPx(activity, 24), ThemeUtils.dpToPx(activity, 64), ThemeUtils.dpToPx(activity, 24), ThemeUtils.dpToPx(activity, 32));

        ImageView titleIcon = new ImageView(activity);
        titleIcon.setImageResource(R.drawable.ic_widgets);
        titleIcon.setColorFilter(adaptiveColor);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(ThemeUtils.dpToPx(activity, 32), ThemeUtils.dpToPx(activity, 32));
        iconLp.rightMargin = ThemeUtils.dpToPx(activity, 16);
        titleLayout.addView(titleIcon, iconLp);

        TextView title = new TextView(activity);
        title.setText(R.string.title_pick_widget);
        title.setTextSize(32f);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setTextColor(adaptiveColor);
        titleLayout.addView(title);
        container.addView(titleLayout);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout itemsContainer = new LinearLayout(activity);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        itemsContainer.setPadding(ThemeUtils.dpToPx(activity, 24), 0, ThemeUtils.dpToPx(activity, 24), ThemeUtils.dpToPx(activity, 24));
        scrollView.addView(itemsContainer);
        container.addView(scrollView);

        for (String pkg : packages) {
            TextView header = new TextView(activity);
            header.setText(activity.getAppName(pkg));
            header.setTextSize(12f);
            header.setTypeface(null, Typeface.BOLD);
            header.setTextColor(secondaryColor);
            header.setAllCaps(true);
            header.setPadding(0, ThemeUtils.dpToPx(activity, 24), 0, ThemeUtils.dpToPx(activity, 12));
            itemsContainer.addView(header);

            for (AppWidgetProviderInfo info : grouped.get(pkg)) {
                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(ThemeUtils.dpToPx(activity, 16), ThemeUtils.dpToPx(activity, 16), ThemeUtils.dpToPx(activity, 16), ThemeUtils.dpToPx(activity, 16));
                card.setClickable(true);
                card.setFocusable(true);

                GradientDrawable cardBg = new GradientDrawable();
                boolean isNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                int cardColor = settingsManager.isLiquidGlass() ? 0x1AFFFFFF : (isNight ? 0x1AFFFFFF : 0x0D000000);
                cardBg.setColor(cardColor);
                cardBg.setCornerRadius(ThemeUtils.dpToPx(activity, 16));
                card.setBackground(cardBg);

                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = ThemeUtils.dpToPx(activity, 12);
                itemsContainer.addView(card, cardLp);

                ImageView preview = new ImageView(activity);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                float density = activity.getResources().getDisplayMetrics().density;

                int cellWidth = activity.getResources().getDisplayMetrics().widthPixels / HomeView.GRID_COLUMNS;
                int cellHeight = activity.getResources().getDisplayMetrics().heightPixels / HomeView.GRID_ROWS;

                int spanX = Math.max(1, (int) Math.ceil(info.minWidth * density / cellWidth));
                int spanY = Math.max(1, (int) Math.ceil(info.minHeight * density / cellHeight));

                widgetPreviewExecutor.execute(() -> {
                    try {
                        android.graphics.drawable.Drawable previewDrawable = info.loadPreviewImage(activity, 0);
                        if (previewDrawable == null) previewDrawable = info.loadIcon(activity, 0);
                        final android.graphics.drawable.Drawable finalDrawable = previewDrawable;
                        activity.runOnUiThread(() -> preview.setImageDrawable(finalDrawable));
                    } catch (Exception ignored) {}
                });
                card.addView(preview, new LinearLayout.LayoutParams(ThemeUtils.dpToPx(activity, 64), ThemeUtils.dpToPx(activity, 64)));

                LinearLayout textLayout = new LinearLayout(activity);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                textLayout.setPadding(ThemeUtils.dpToPx(activity, 16), 0, 0, 0);

                TextView label = new TextView(activity);
                label.setText(info.loadLabel(activity.getPackageManager()));
                label.setTextColor(adaptiveColor);
                label.setTextSize(16f);
                label.setTypeface(null, Typeface.BOLD);
                textLayout.addView(label);

                TextView size = new TextView(activity);
                size.setText(activity.getString(R.string.widget_size_format, spanX, spanY));
                size.setTextSize(12f);
                size.setTextColor(secondaryColor);
                textLayout.addView(size);

                card.addView(textLayout);

                card.setOnClickListener(v -> {
                    Toast.makeText(activity, "Long press to drag widget", Toast.LENGTH_SHORT).show();
                });

                card.setOnLongClickListener(v -> {
                    dialog.dismiss();
                    activity.startNewWidgetDrag(info, spanX, spanY);
                    return true;
                });
            }
        }

        ImageView closeBtn = new ImageView(activity);
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setColorFilter(adaptiveColor);
        closeBtn.setAlpha(0.6f);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(ThemeUtils.dpToPx(activity, 48), ThemeUtils.dpToPx(activity, 48), Gravity.TOP | Gravity.END);
        closeLp.topMargin = ThemeUtils.dpToPx(activity, 16);
        closeLp.rightMargin = ThemeUtils.dpToPx(activity, 16);
        root.addView(closeBtn, closeLp);

        dialog.setContentView(root);
        dialog.show();
    }

    public void showWidgetOptions(HomeItem item, View hostView) {
        String[] options = {activity.getString(R.string.action_resize), activity.getString(R.string.action_remove)};
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setItems(options, (d, which) -> {
                    if (which == 0) showResizeDialog(item, hostView);
                    else activity.removeHomeItem(item, hostView);
                }).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ThemeUtils.getGlassDrawable(activity, settingsManager, 28));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }
    }

    public void showResizeDialog(HomeItem item, View hostView) {
        String[] sizes = {"1x1", "2x1", "2x2", "4x2", "4x1"};
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.title_resize_widget)
                .setItems(sizes, (d, which) -> {
                    switch (which) {
                        case 0: item.spanX = 1; item.spanY = 1; break;
                        case 1: item.spanX = 2; item.spanY = 1; break;
                        case 2: item.spanX = 2; item.spanY = 2; break;
                        case 3: item.spanX = 4; item.spanY = 2; break;
                        case 4: item.spanX = 4; item.spanY = 1; break;
                    }
                    activity.getHomeView().updateViewPosition(item, hostView);
                    activity.saveHomeState();
                }).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ThemeUtils.getGlassDrawable(activity, settingsManager, 28));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }
    }
}
