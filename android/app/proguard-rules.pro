# Capacitor ships the keep rules its bridge and @CapacitorPlugin classes need
# (including WaymarkPlugin). AndroidX libraries ship their own consumer rules.

# Home-screen widget: Glance calls GlanceAppWidget.provideGlance() from its
# session worker, but R8 (full mode) concludes the override is unreachable and
# strips the whole widget UI from release builds (debug builds are fine).
# Keep the entry point; everything it composes stays reachable from here.
-keep class com.waymark.widget.NextUpWidget {
    public <init>();
    *** provideGlance(...);
}
