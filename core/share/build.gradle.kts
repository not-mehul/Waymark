plugins {
    id("waymark.jvm.serialization")
}

// Trip files, backups, QR payloads, shared emails and boarding passes.
dependencies {
    api(projects.core.catalog)
}
