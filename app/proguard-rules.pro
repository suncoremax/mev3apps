-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep public class com.miron.electronics.stock.MainActivity$AndroidBridge
-keepclassmembers class com.miron.electronics.stock.MainActivity$AndroidBridge {
    public *;
}
