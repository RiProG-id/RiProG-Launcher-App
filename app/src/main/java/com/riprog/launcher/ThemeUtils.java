package com.riprog.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class ThemeUtils {

    public static Drawable getGlassDrawable(Context context, SettingsManager settingsManager) {
        return getGlassDrawable(context, settingsManager, 28f);
    }

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
        return (luminance > 0.5) ? context.getColor(R.color.foreground) : Color.WHITE;
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
            if (isNight && settingsManager.isDarkenWallpaper()) {
                return Color.WHITE;
            } else if (isNight) {
                return Color.WHITE;
            } else {
                return Color.BLACK;
            }
        }
    }

    public static void applyBlurIfSupported(View view, boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                view.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP));
            } else {
                view.setRenderEffect(null);
            }
        }
    }

    public static void applyWindowBlur(Window window, boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WindowManager.LayoutParams lp = window.getAttributes();
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                lp.setBlurBehindRadius(60);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                lp.setBlurBehindRadius(0);
            }
            window.setAttributes(lp);
        }
    }

    public static void updateStatusBarContrast(Activity activity) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        boolean isNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!isNight);
    }

    private static int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
