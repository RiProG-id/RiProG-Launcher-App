package com.riprog.launcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WidgetManager {
    private final MainActivity activity;
    private final SettingsManager settingsManager;
    private final AppWidgetManager appWidgetManager;
    private final AppWidgetHost appWidgetHost;
    private final GridManager gridManager;
    private final ExecutorService widgetPreviewExecutor = Executors.newFixedThreadPool(4);

    public WidgetManager(MainActivity activity, SettingsManager settingsManager, AppWidgetManager appWidgetManager, AppWidgetHost appWidgetHost) {
        this.activity = activity;
        this.settingsManager = settingsManager;
        this.appWidgetManager = appWidgetManager;
        this.appWidgetHost = appWidgetHost;
        this.gridManager = new GridManager(settingsManager.getColumns());
    }

    public void pickWidget(float lastGridCol, float lastGridRow) {
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
        titleLayout.setPadding(0, 0, 0, dpToPx(32));

        ImageView titleIcon = new ImageView(activity);
        titleIcon.setImageResource(R.drawable.ic_widgets);
        titleIcon.setColorFilter(adaptiveColor);
        titleLayout.addView(titleIcon, new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)));

        TextView title = new TextView(activity);
        title.setText(R.string.title_pick_widget);
        title.setTextSize(32f);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setTextColor(adaptiveColor);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = dpToPx(16);
        titleLayout.addView(title, titleParams);
        container.addView(titleLayout);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout itemsContainer = new LinearLayout(activity);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(itemsContainer);
        container.addView(scrollView);

        for (String pkg : packages) {
            TextView header = new TextView(activity);
            header.setText(activity.getAppName(pkg));
            header.setTextSize(12f);
            header.setTypeface(null, Typeface.BOLD);
            header.setTextColor(secondaryColor);
            header.setAllCaps(true);
            header.setPadding(0, dpToPx(24), 0, dpToPx(12));
            itemsContainer.addView(header);

            List<AppWidgetProviderInfo> infos = grouped.get(pkg);
            if (infos != null) {
                for (AppWidgetProviderInfo info : infos) {
                    LinearLayout card = new LinearLayout(activity);
                    card.setOrientation(LinearLayout.HORIZONTAL);
                    card.setGravity(Gravity.CENTER_VERTICAL);
                    card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                    card.setClickable(true);
                    card.setFocusable(true);

                    GradientDrawable cardBg = new GradientDrawable();
                    boolean isNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES;
                    int cardColor = settingsManager.isLiquidGlass() ? 0x1AFFFFFF : (isNight ? 0x1AFFFFFF : 0x0D000000);
                    cardBg.setColor(cardColor);
                    cardBg.setCornerRadius(dpToPx(16));
                    card.setBackground(cardBg);

                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cardLp.bottomMargin = dpToPx(12);
                    itemsContainer.addView(card, cardLp);

                    ImageView preview = new ImageView(activity);
                    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    float density = activity.getResources().getDisplayMetrics().density;

                    int cellWidth = gridManager.getCellWidth(activity.homeView.getWidth() > 0 ? activity.homeView.getWidth() : activity.getResources().getDisplayMetrics().widthPixels);
                    int cellHeight = gridManager.getCellHeight(activity.homeView.getHeight() > 0 ? activity.homeView.getHeight() : activity.getResources().getDisplayMetrics().heightPixels);

                    float sX = gridManager.calculateSpanX(info.minWidth * density, cellWidth);
                    float sY = gridManager.calculateSpanY(info.minHeight * density, cellHeight);

                    if (!settingsManager.isFreeformHome()) {
                        sX = Math.max(1, Math.round(sX));
                        sY = Math.max(1, Math.round(sY));
                    }
                    final float spanX = sX;
                    final float spanY = sY;

                    widgetPreviewExecutor.execute(() -> {
                        try {
                            Drawable previewDrawable = info.loadPreviewImage(activity, 0);
                            if (previewDrawable == null) previewDrawable = info.loadIcon(activity, 0);
                            final Drawable finalDrawable = previewDrawable;
                            activity.runOnUiThread(() -> preview.setImageDrawable(finalDrawable));
                        } catch (Exception ignored) { }
                    });
                    card.addView(preview, new LinearLayout.LayoutParams(dpToPx(64), dpToPx(64)));

                    LinearLayout textLayout = new LinearLayout(activity);
                    textLayout.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    textLp.leftMargin = dpToPx(16);
                    card.addView(textLayout, textLp);

                    TextView label = new TextView(activity);
                    label.setText(info.loadLabel(activity.getPackageManager()));
                    label.setTextColor(adaptiveColor);
                    label.setTextSize(16f);
                    label.setTypeface(null, Typeface.BOLD);
                    textLayout.addView(label);

                    TextView size = new TextView(activity);
                    size.setText(activity.getString(R.string.widget_size_format, (int)Math.ceil(spanX), (int)Math.ceil(spanY)));
                    size.setTextSize(12f);
                    size.setTextColor(secondaryColor);
                    textLayout.addView(size);

                    card.setOnClickListener(v -> Toast.makeText(activity, "Long press to drag widget", Toast.LENGTH_SHORT).show());
                    card.setOnLongClickListener(v -> {
                        dialog.dismiss();
                        activity.startNewWidgetDrag(info, (int)spanX, (int)spanY);
                        return true;
                    });
                }
            }
        }

        ImageView closeBtn = new ImageView(activity);
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setColorFilter(adaptiveColor);
        closeBtn.setAlpha(0.6f);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.TOP | Gravity.END);
        closeLp.topMargin = dpToPx(16);
        closeLp.rightMargin = dpToPx(16);
        root.addView(closeBtn, closeLp);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            container.setPadding(dpToPx(24), bars.top + dpToPx(64), dpToPx(24), bars.bottom + dpToPx(24));
            closeLp.topMargin = bars.top + dpToPx(16);
            closeBtn.setLayoutParams(closeLp);
            return insets;
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private int dpToPx(float dp) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp, activity.getResources().getDisplayMetrics());
    }
}
