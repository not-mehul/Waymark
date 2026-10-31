plugins {
    id("waymark.jvm.library")
}

// Stations, cities, airlines, countries and the coastline. The two large
// tables ship as classpath resources of this module.
dependencies {
    api(projects.core.domain)
}
