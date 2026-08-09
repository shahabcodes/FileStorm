# Everything here is reached by name rather than by a call R8 can see, so it
# has to be kept explicitly. The default Android rules already cover the
# manifest's activities, services and enum valueOf.

# Kept for readable crash reports; costs a little size, saves a lot of guessing.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The vault reads and writes its own binary format with DataInput/DataOutput,
# not reflection, so nothing there needs keeping. These are the framework
# callbacks resolved by name at runtime.
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# Coil decodes on background threads via reflection-free APIs, but its
# ServiceLoader-based decoder registry needs its entries left alone.
-keep class coil.** { *; }
-dontwarn coil.**

# org.json is part of the platform.
-dontwarn org.json.**
