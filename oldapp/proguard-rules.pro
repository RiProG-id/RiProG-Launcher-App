# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep the main activity and launcher model
-keep public class com.riprog.launcher.MainActivity
-keep public class com.riprog.launcher.LauncherModel { *; }
-keep public class com.riprog.launcher.AppItem { *; }

# Keep AppWidget classes
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.appwidget.AppWidgetHost { *; }
-keep class * extends android.appwidget.AppWidgetHostView { *; }
