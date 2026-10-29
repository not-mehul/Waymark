pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// `projects.core.data` rather than `project(":core:data")` in build files.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Waymark"

include(":app")
include(":core:domain", ":core:catalog", ":core:share")
include(":core:data", ":core:designsystem", ":core:notifications")
include(":feature:shelf", ":feature:people", ":feature:share", ":feature:flight", ":feature:trip")
