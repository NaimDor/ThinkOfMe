# Firestore maps documents manually (no reflection-based toObject), so no model keep rules are needed.
# Keep kotlinx.serialization route classes used by Navigation.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
