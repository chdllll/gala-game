# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK.
# For more details, see
#   https://developer.android.com/build/shrink-code

# Keep Python related classes
-keep class com.chaquo.** { *; }
-keep class org.python.** { *; }

# Keep all Python objects
-keep class * implements com.chaquo.python.PyObject { *; }
