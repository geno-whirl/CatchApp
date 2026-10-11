# kotlinx.serialization: keep generated serializers for the offline edit queue and settings.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.genowhirl.catchup.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.genowhirl.catchup.**$$serializer { *; }
# SnakeYAML Engine uses reflection-free APIs, but keep it intact.
-keep class org.snakeyaml.engine.** { *; }
-dontwarn org.snakeyaml.engine.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
