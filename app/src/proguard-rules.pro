
# Trip files and backups are encoded with kotlinx.serialization; keep the
# generated serializers the archive code looks up by companion.
-keepclassmembers @kotlinx.serialization.Serializable class com.waymark.data.share.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
