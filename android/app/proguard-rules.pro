# Firestore maps documents manually (no reflection-based toObject), so no model keep rules are needed.
# Keep kotlinx.serialization route classes used by Navigation.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Room databases are created by name at runtime ("<Database>_Impl" via Class.forName), including
# WorkManager's own WorkDatabase, which Firebase and Google Mobile Ads start at app launch.
# R8 full mode (AGP 9) renamed it, crashing every release build on startup with
# "Failed to create an instance of androidx.work.impl.WorkDatabase". Keep the generated
# database classes' names and no-arg constructors.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepnames class * extends androidx.room.RoomDatabase
# androidx.startup initializers (WorkManagerInitializer and friends) are also created by name
# from the manifest.
-keep class * implements androidx.startup.Initializer { <init>(); }
