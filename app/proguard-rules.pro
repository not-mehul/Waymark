# Room generates implementations reflectively referenced by name.
-keep class com.waymark.data.local.** { *; }

# Keep WorkManager workers instantiated by reflection.
-keep class com.waymark.alerts.** extends androidx.work.ListenableWorker { *; }

# Trip files, backups and saved navigation keys are encoded with
# kotlinx.serialization; keep the generated serializers that are looked up by
# companion at run time.
-keepclassmembers @kotlinx.serialization.Serializable class com.waymark.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
