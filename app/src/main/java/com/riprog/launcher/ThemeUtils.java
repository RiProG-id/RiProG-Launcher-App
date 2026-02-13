package com.riprog.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

public class ThemeUtils {
    public static Drawable getGlassDrawable(Context context, SettingsManager settingsManager) {
        boolean isLiquidGlass = settingsManager.isLiquidGlass();

        GradientDrawable gd = new GradientDrawable();
        int backgroundColor = context.getColor(R.color.background);

        if (!isLiquidGlass) {
            // Make it 90% opaque (0xE6 alpha) if disabled
            backgroundColor = (backgroundColor & 0x00FFFFFF) | 0xE6000000;
        }

        gd.setColor(backgroundColor);
        gd.setCornerRadius(dpToPx(context, 28));

        if (isLiquidGlass) {
            gd.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.glass_stroke));
        }

        return gd;
    }

    private static int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
