# Luteal R8/ProGuard rules

# --- kotlinx.serialization ---
# Keep the generated serializer companions and @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable contract DTOs and their serializers.
-keep,includedescriptorclasses class fr.luteal.core.network.contract.models.**$$serializer { *; }
-keepclassmembers class fr.luteal.core.network.contract.models.** {
    *** Companion;
}
-keepclasseswithmembers class fr.luteal.core.network.contract.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep app-level @Serializable types (SyncWire, etc.).
-keep,includedescriptorclasses class fr.luteal.core.network.**$$serializer { *; }
-keepclassmembers class fr.luteal.core.network.** {
    *** Companion;
}
-keepclasseswithmembers class fr.luteal.core.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep model @Serializable types (LutealBackupPayload, backup DTOs, etc.).
-keep,includedescriptorclasses class fr.luteal.core.model.**$$serializer { *; }
-keepclassmembers class fr.luteal.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class fr.luteal.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ---
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# --- ACRA ---
-keep class org.acra.** { *; }
-dontwarn org.acra.**

# --- Java time (desugared on older APIs) ---
-dontwarn java.time.**

# --- Keep enum values used in switch/when ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
