plugins {
    id("waymark.jvm.library")
}

dependencies {
    // The worked example the domain tests run against lives in the catalogue.
    testImplementation(projects.core.catalog)
}
