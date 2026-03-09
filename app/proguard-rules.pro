
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-dontpreverify


-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}


-keep public class com.riprog.launcher.LauncherApplication
-keep public class com.riprog.launcher.features.main.MainActivity
-keep public class com.riprog.launcher.features.settings.SettingsActivity
-keep public class com.riprog.launcher.features.widgets.WidgetPickerActivity


-keep class com.riprog.launcher.data.models.HomeItem { *; }
-keep class com.riprog.launcher.data.models.HomeItem$Type { *; }
-keep class com.riprog.launcher.data.models.AppItem { *; }


-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.appwidget.AppWidgetHost { *; }
-keep class * extends android.appwidget.AppWidgetHostView { *; }


-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn androidx.**
