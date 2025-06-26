-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class com.android.vending.licensing.ILicensingService

-keepclasseswithmembers class * {
    native <methods>;
}

-keepclassmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclassmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}

-keep enum ** {
    **[] $VALUES;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.riprog.launcher.MainActivity$AppListWrapper { *; }

-keepclassmembers class * extends android.os.AsyncTask {
    void onPreExecute();
    void onPostExecute(...);
    void doInBackground(...);
    void onProgressUpdate(...);
    void onCancelled(...);
}

-keep class com.riprog.launcher.MainActivity$ViewHolder { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
