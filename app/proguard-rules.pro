# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# R8 9.3.16 emits invalid DEX for this large composable in profileServer:
# a Composer reference is used by or-int at 0x1f. Keep only this method's
# optimization disabled; shrinking and obfuscation remain enabled. Revalidate
# and remove after the compiler defect is resolved. This is not a Compose-wide
# keep rule or a reflection requirement.
-keepclassmembers,allowshrinking,allowobfuscation class at.bernhardberger.tvhplayer.ui.screens.EpgGridScreenKt {
    public static void EpgGridScreen(...);
}
