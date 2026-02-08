package com.riprog.launcher;

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

    public HomeView(Context context) {
        super(context);
        setBackgroundResource(R.color.background);

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
        if (!isAttachedToWindow()) return;
        Calendar cal = Calendar.getInstance();
        clockView.setText(DateFormat.getTimeFormat(getContext()).format(cal.getTime()));
        dateView.setText(DateFormat.getMediumDateFormat(getContext()).format(cal.getTime()));
        postDelayed(this::updateClock, 10000);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateClock();
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
                Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(item.packageName);
                if (intent != null) getContext().startActivity(intent);
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

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
