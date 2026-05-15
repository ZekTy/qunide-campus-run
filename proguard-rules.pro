# Keep map SDK classes that are commonly loaded through reflection or native bridges.
-keep class com.amap.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }

# Keep JSON model names if future route imports/exports depend on reflection.
-keep class org.json.** { *; }
