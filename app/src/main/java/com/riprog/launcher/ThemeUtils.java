package com.riprog.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

public class ThemeUtils {
    public static Drawable getGlassDrawable(Context context, SettingsManager settingsManager) {
        return getGlassDrawable(context, settingsManager, 28);
    }

    public static Drawable getGlassDrawable(Context context, SettingsManager settingsManager, float cornerRadiusDp) {
        boolean isLiquidGlass = settingsManager.isLiquidGlass();

        GradientDrawable gd = new GradientDrawable();
        int backgroundColor = context.getColor(R.color.background);

        if (!isLiquidGlass) {
            backgroundColor = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES ? Color.BLACK : Color.WHITE;
        }

        gd.setColor(backgroundColor);
        gd.setCornerRadius(dpToPx(context, cornerRadiusDp));

        if (isLiquidGlass) {
            gd.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.glass_stroke));
        }

        return gd;
    }

    private static int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
