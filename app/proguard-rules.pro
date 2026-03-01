-optimizationpasses 5
-allowaccessmodification
-dontpreverify

-keep public class com.riprog.launcher.ui.activities.MainActivity
-keep public class com.riprog.launcher.LauncherApplication
-keep public class com.riprog.launcher.ui.activities.SettingsActivity
-keep public class com.riprog.launcher.ui.activities.WidgetPickerActivity

-keep class com.riprog.launcher.data.model.HomeItem { *; }
-keep class com.riprog.launcher.data.model.AppItem { *; }

-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.appwidget.AppWidgetHost { *; }
-keep class * extends android.appwidget.AppWidgetHostView { *; }

-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn androidx.**
