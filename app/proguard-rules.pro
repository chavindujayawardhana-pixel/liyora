# Liyora R8 rules.
#
# Most of the app needs no rules: the activity and the keyboard service are kept through the
# manifest, keyboard views are created in code (not inflated from XML), and Gemini calls use
# HttpURLConnection + org.json from the platform, with no reflection.

# Tink (behind EncryptedSharedPreferences) references annotation classes that exist only at
# compile time. They are not needed at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Tink stores keysets as protobuf-lite messages, which read their fields by name. If these are
# renamed or removed, reading the saved Gemini key fails only in release builds.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# No logging is done anywhere, but strip any android.util.Log calls a library might add.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
