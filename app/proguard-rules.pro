# LibreHU: R8 shrinks and optimises the code (Jetpack Compose is much faster that way), without renaming it: logcat
# stack traces stay readable and the classes named in the manifest or by AIDL keep their names.
-dontobfuscate

-keepattributes *Annotation*
-keep class javax.inject.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
