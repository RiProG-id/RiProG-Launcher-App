package com.riprog.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class ThemeUtils {

    public static Drawable getGlassDrawable(Context context, SettingsManager settingsManager, float cornerRadiusDp) {
        boolean isLiquidGlass = settingsManager.isLiquidGlass();
        boolean isNight = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;

        GradientDrawable gd = new GradientDrawable();
        int backgroundColor;
        if (isLiquidGlass) {
            backgroundColor = context.getColor(R.color.background);
        } else {
            backgroundColor = isNight ? Color.BLACK : Color.WHITE;
        }

        gd.setColor(backgroundColor);
        gd.setCornerRadius(dpToPx(context, cornerRadiusDp));

        if (isLiquidGlass) {
            gd.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.glass_stroke));
        }

        return gd;
    }

    public static int getAdaptiveColor(Context context, int backgroundColor) {
        double luminance = (0.2126 * Color.red(backgroundColor) +
                0.7152 * Color.green(backgroundColor) +
                0.0722 * Color.blue(backgroundColor)) / 255.0;
        return luminance > 0.5 ? context.getColor(R.color.foreground) : Color.WHITE;
    }

    public static int getAdaptiveColor(Context context, SettingsManager settingsManager, boolean isOnGlass) {
        boolean isNight = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isOnGlass) {
            if (settingsManager.isLiquidGlass()) {
                return getAdaptiveColor(context, context.getColor(R.color.background));
            } else {
                return isNight ? Color.WHITE : Color.BLACK;
            }
        } else {
            return isNight ? Color.WHITE : Color.BLACK;
        }
    }

    public static void applyBlurIfSupported(View view, boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                view.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP));
            } else {
                view.setRenderEffect(null);
            }
        }
    }

    public static void applyWindowBlur(Window window, boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                WindowManager.LayoutParams lp = window.getAttributes();
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    lp.getClass().getField("blurBehindRadius").set(lp, 60);
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    lp.getClass().getField("blurBehindRadius").set(lp, 0);
                }
                window.setAttributes(lp);
            } catch (Exception ignored) {}
        }
    }

    public static void updateStatusBarContrast(Activity activity) {
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        boolean isNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                isNight ? 0 : android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
        } else {
            int flags = decorView.getSystemUiVisibility();
            if (isNight) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        }
    }

    public static void applySettingItemStyle(Context context, View item, SettingsManager settingsManager) {
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

        int rippleColor = context.getColor(R.color.search_background);

        item.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleColor),
                shape,
                mask
        ));
    }

    public static int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
