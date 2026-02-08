package com.riprog.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

public class HomeView extends FrameLayout {
    private final TextView clockView;
    private final TextView dateView;
    private final GridLayout favoritesGrid;
    private final FrameLayout widgetContainer;
    private final ImageView settingsIcon;
    private final Runnable clockRunnable = this::updateClock;

    public HomeView(Context context) {
        super(context);
        setBackgroundResource(R.color.background);

        settingsIcon = new ImageView(context);
        settingsIcon.setImageResource(android.R.drawable.ic_menu_preferences);
        settingsIcon.setAlpha(0.5f);
        int iconSize = dpToPx(24);
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        settingsParams.gravity = Gravity.TOP | Gravity.END;
        settingsParams.setMargins(0, dpToPx(48), dpToPx(24), 0);
        settingsIcon.setLayoutParams(settingsParams);
        settingsIcon.setOnClickListener(v -> {
            Intent intent = new Intent(context, SettingsActivity.class);
            if (context instanceof Activity) {
                ((Activity) context).startActivityForResult(intent, 100);
            } else {
                context.startActivity(intent);
            }
        });
        addView(settingsIcon);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        addView(root);

        clockView = new TextView(context);
        clockView.setTextSize(64);
        clockView.setTextColor(context.getColor(R.color.foreground));
        clockView.setGravity(Gravity.CENTER);
        clockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        root.addView(clockView);

        dateView = new TextView(context);
        dateView.setTextSize(18);
        dateView.setTextColor(context.getColor(R.color.foreground_dim));
        dateView.setGravity(Gravity.CENTER);
        root.addView(dateView);

        favoritesGrid = new GridLayout(context);
        favoritesGrid.setColumnCount(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dpToPx(48);
        root.addView(favoritesGrid, params);

        widgetContainer = new FrameLayout(context);
        LinearLayout.LayoutParams widgetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(100));
        widgetParams.topMargin = dpToPx(24);
        root.addView(widgetContainer, widgetParams);
    }

    private void updateClock() {
        if (clockView == null || dateView == null) return;
        Calendar cal = Calendar.getInstance();
        clockView.setText(DateFormat.getTimeFormat(getContext()).format(cal.getTime()));
        dateView.setText(DateFormat.getMediumDateFormat(getContext()).format(cal.getTime()));
        postDelayed(clockRunnable, 10000);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateClock();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(clockRunnable);
    }

    public void setFavorites(List<AppItem> favorites, LauncherModel model) {
        favoritesGrid.removeAllViews();
        for (AppItem item : favorites) {
            ImageView iconView = new ImageView(getContext());
            int size = dpToPx(48);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            int margin = dpToPx(16);
            lp.setMargins(margin, margin, margin, margin);
            iconView.setLayoutParams(lp);
            iconView.getLayoutParams().width = size;
            iconView.getLayoutParams().height = size;

            if (model != null) {
                model.loadIcon(item, iconView::setImageBitmap);
            }
            iconView.setOnClickListener(v -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction(() -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    new SettingsManager(getContext()).incrementUsage(item.packageName);
                    Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(item.packageName);
                    if (intent != null) getContext().startActivity(intent);
                }).start();
            });
            favoritesGrid.addView(iconView);
        }
    }

    public void setWidget(View widget) {
        widgetContainer.removeAllViews();
        if (widget != null) {
            widgetContainer.addView(widget);
        }
    }

    public void setAccentColor(int color) {
        if (clockView != null) clockView.setTextColor(color);
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
