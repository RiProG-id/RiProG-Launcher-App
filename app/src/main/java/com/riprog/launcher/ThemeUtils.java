package com.riprog.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

public class ThemeUtils {
    public static Drawable getGlassDrawable(Context context, SettingsManager settingsManager) {
        return getGlassDrawable(context, settingsManager, 28);
    }

    public static Drawable getGlassDrawable(Context context, SettingsManager settingsManager, float cornerRadiusDp) {
        boolean isLiquidGlass = settingsManager.isLiquidGlass();
        boolean isNight = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

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
        boolean isNight = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isOnGlass) {
            if (settingsManager.isLiquidGlass()) {
                // In Liquid Glass mode, we use the themed background color's luminance
                return getAdaptiveColor(context, context.getColor(R.color.background));
            } else {
                // Flat mode: Black on White, White on Black
                return isNight ? Color.WHITE : Color.BLACK;
            }
        } else {
            // Home screen (over wallpaper)
            // If darkening is enabled and it's night, we definitely want white
            if (isNight && settingsManager.isDarkenWallpaper()) {
                return Color.WHITE;
            }
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
            if (enabled) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(60);
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(0);
            }
        }
    }

    public static void updateStatusBarContrast(android.app.Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Window window = activity.getWindow();
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                boolean isNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                if (isNight) {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                } else {
                    controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        } else {
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
            boolean isNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            if (isNight) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private static int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
