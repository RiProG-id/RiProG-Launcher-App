# Optimization flags
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-dontpreverify

# Rule to strip logs in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Keep rules for Android components
-keep public class com.riprog.launcher.LauncherApplication
-keep public class com.riprog.launcher.features.main.MainActivity
-keep public class com.riprog.launcher.features.settings.SettingsActivity
-keep public class com.riprog.launcher.features.widgets.WidgetPickerActivity

# Keep rules for data models (used for serialization/deserialization)
-keep class com.riprog.launcher.data.models.HomeItem { *; }
-keep class com.riprog.launcher.data.models.HomeItem$Type { *; }
-keep class com.riprog.launcher.data.models.AppItem { *; }

# Keep rules for AppWidget components
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.appwidget.AppWidgetHost { *; }
-keep class * extends android.appwidget.AppWidgetHostView { *; }

# Standard attributes to keep
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn androidx.**
