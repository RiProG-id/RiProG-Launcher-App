# Core launcher classes
-keep public class com.riprog.launcher.ui.activities.MainActivity
-keep public class com.riprog.launcher.ui.activities.SettingsActivity
-keep public class com.riprog.launcher.LauncherApplication

# Data models
-keep class com.riprog.launcher.data.model.** { *; }
-keep class com.riprog.launcher.data.cache.** { *; }

# AppWidget classes
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.appwidget.AppWidgetHost { *; }
-keep class * extends android.appwidget.AppWidgetHostView { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext$HandlerPost {
    *** run();
}

# AndroidX Core
-keep class androidx.core.** { *; }

# Suppress warnings if any
-dontwarn kotlinx.coroutines.**
