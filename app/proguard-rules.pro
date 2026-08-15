# Room generates implementations reflectively referenced by name.
-keep class com.waymark.data.local.** { *; }

# Keep WorkManager workers instantiated by reflection.
-keep class com.waymark.alerts.** extends androidx.work.ListenableWorker { *; }
