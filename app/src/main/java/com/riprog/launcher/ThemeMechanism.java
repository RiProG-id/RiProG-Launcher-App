package com.riprog.launcher;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.widget.LinearLayout;

public class ThemeMechanism {

    public static void applyThemeMode(Context context, String mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            int nightMode = UiModeManager.MODE_NIGHT_AUTO;
            if ("light".equals(mode)) nightMode = UiModeManager.MODE_NIGHT_NO;
            else if ("dark".equals(mode)) nightMode = UiModeManager.MODE_NIGHT_YES;

            if (uiModeManager.getNightMode() != nightMode) {
                uiModeManager.setApplicationNightMode(nightMode);
            }
        }
    }

    public static Integer getSystemAccentColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getResources().getColor(android.R.color.system_accent1_400, context.getTheme());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public static void applySettingItemStyle(Context context, LinearLayout item, SettingsManager settingsManager) {
        item.setClickable(true);
        item.setFocusable(true);

        float radius = dpToPx(context, 12);
        boolean isNight = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;

        GradientDrawable shape = new GradientDrawable();
        int baseColor;
        if (settingsManager.isLiquidGlass()) {
            baseColor = isNight ? 0x26FFFFFF : 0x1A000000;
        } else {
            baseColor = isNight ? 0x1AFFFFFF : 0x0D000000;
        }
        shape.setColor(baseColor);
        shape.setCornerRadius(radius);
        if (settingsManager.isLiquidGlass()) {
            shape.setStroke(dpToPx(context, 1), 0x20FFFFFF);
        }

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        mask.setCornerRadius(radius);

        int rippleColor;
        try {
            rippleColor = context.getColor(R.color.search_background);
        } catch (Exception e) {
            rippleColor = 0x40FFFFFF;
        }

        item.setBackground(new RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            shape,
            mask
        ));
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, (float) dp, context.getResources().getDisplayMetrics()
        );
    }
}
