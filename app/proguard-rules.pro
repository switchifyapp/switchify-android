# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

# Keep MediaPipe, TFLite, and Protobuf classes used by facial recognition
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.protobuf.** { *; }

# Keep native methods to preserve JNI bindings
-keepclasseswithmembers class * {
    native <methods>;
}

# Preserve attributes often needed for reflection
-keepattributes RuntimeVisibleAnnotations,EnclosingMethod,InnerClasses

# Don’t warn on third‑party internals
-dontwarn com.google.mediapipe.**
-dontwarn org.tensorflow.lite.**

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
