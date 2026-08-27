# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson: keep all data classes used for JSON parsing
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.pupr.kalsel.riverop.data.remote.** { *; }

# Room: keep entity and DAO classes
-keep class com.pupr.kalsel.riverop.data.local.entity.** { *; }
-keep class com.pupr.kalsel.riverop.data.local.dao.** { *; }

# Enum: keep SyncStatus label field used at runtime
-keepclassmembers enum com.pupr.kalsel.riverop.models.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
