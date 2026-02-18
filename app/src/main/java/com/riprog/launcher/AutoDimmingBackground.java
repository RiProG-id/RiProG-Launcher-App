package com.riprog.launcher;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class AutoDimmingBackground {
    private final Context context;
    private final ViewGroup parentLayout;
    private final SettingsManager settingsManager;
    private View dimView;

    public AutoDimmingBackground(Context context, ViewGroup parentLayout, SettingsManager settingsManager) {
        this.context = context;
        this.parentLayout = parentLayout;
        this.settingsManager = settingsManager;
        setupDimView();
    }

    private void setupDimView() {
        dimView = new View(context);
        dimView.setBackgroundColor(Color.BLACK);
        dimView.setAlpha(0.3f);
        dimView.setVisibility(View.GONE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        parentLayout.addView(dimView, 0, lp);
    }

    public void updateDimVisibility() {
        boolean isNight = (context.getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isNight && settingsManager.isDarkenWallpaper()) {
            dimView.setVisibility(View.VISIBLE);
            dimView.animate().alpha(0.3f).setDuration(300).start();
        } else {
            dimView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                dimView.setVisibility(View.GONE);
            }).start();
        }
    }
}
