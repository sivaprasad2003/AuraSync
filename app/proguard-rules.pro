# Keep Kotlinx Serialization generated serializers for PairingToken & Config Export/Import
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.enterprise.syncpro.data.**$$serializer { *; }
-keepclassmembers class com.enterprise.syncpro.data.PairingToken {
    *** Companion;
}
-keepclassmembers class com.enterprise.syncpro.data.ExportedConfiguration {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class com.enterprise.syncpro.data.** { *; }

# Keep Room Database Entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Google Tink / EncryptedSharedPreferences (AES-256-GCM)
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# ZXing QR Engine
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }