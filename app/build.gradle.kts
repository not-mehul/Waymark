import java.util.Properties

// The app shell: the activity, navigation, the dependency graph, reminders and
// the widget. Screens live in :feature:*, everything below them in :core:*.
plugins {
    id("waymark.android.application")
    id("waymark.hilt")
}

/**
 * The version, from the release tag.
 *
 * `release.yml` passes `-PwaymarkVersion=1.4.2` from a `v1.4.2` tag, so the
 * number inside the APK is the number on the release page and nobody has to
 * remember to bump it in two places. The version code is derived from it —
 * major × 10 000 + minor × 100 + patch — which keeps every later release
 * installable over the one before. Without the property (a local or CI build)
 * it is 0.0.0, with a code of 1.
 */
val waymarkVersion: String = (findProperty("waymarkVersion") as String?)
    ?.removePrefix("v")
    ?.takeIf { Regex("""\d+\.\d+\.\d+""").matches(it) }
    ?: "0.0.0"

val waymarkVersionCode: Int = waymarkVersion.split('.').map(String::toInt)
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
    .coerceAtLeast(1)

/**
 * Where the release key comes from.
 *
 * Two sources, in order: a `keystore.properties` beside this file for building
 * on your own machine, and four environment variables for building in CI. Both
 * are optional — with neither, `assembleRelease` still runs and produces an
 * unsigned APK, which is what you want for inspecting a build but cannot be
 * installed. None of it is ever committed: the properties file and every
 * `*.jks`/`*.keystore` are in `.gitignore`.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

/**
 * One signing value, from the properties file or the environment.
 *
 * The cleaning is not decoration. `Properties.load` keeps trailing whitespace,
 * so `storePassword=hunter2 ` — with a space nobody can see before the newline
 * — reaches keytool as a different password and fails with "keystore password
 * was incorrect", which sends you looking at the keystore rather than at the
 * line that describes it. Surrounding quotes are stripped for the same reason:
 * this is a Java properties file, not a shell, and `storePassword="hunter2"`
 * would otherwise make the quotes part of the password.
 *
 * A backslash is the one thing that cannot be rescued here — `Properties`
 * treats it as an escape — so a password containing one must be doubled in the
 * file, or passed through the environment instead.
 */
fun signingValue(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
        ?.takeIf { it.isNotBlank() }

// Named `release*` rather than `storeFile`/`storePassword` so nothing shadows
// the identically named properties inside the `signingConfigs` block below,
// where `storePassword = storePassword` would silently assign to itself.
val releaseStoreFile = signingValue("storeFile", "WAYMARK_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "WAYMARK_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "WAYMARK_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "WAYMARK_KEY_PASSWORD")
val canSignRelease =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { it != null } && rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "com.waymark"

    defaultConfig {
        applicationId = "com.waymark"
        versionCode = waymarkVersionCode
        versionName = waymarkVersion
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // The baseline-profile plugin adds release-like build types to
        // measure and profile; they are only ever installed on a test device,
        // so the debug key signs them whether or not a release key exists.
        matching { it.name == "nonMinifiedRelease" || it.name == "benchmarkRelease" }.configureEach {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        // The About sheet prints the version it is running; this is where it
        // comes from, rather than a second copy of the number in the source.
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // Lint runs in CI on every push; a warning is a note, an error is a stop.
        abortOnError = true
        // The libraries are linted as part of the app, with the app's view
        // of what is used and what is not.
        checkDependencies = true
        // A plain-text report beside the HTML one, which CI prints in full.
        textReport = true
        // The app asks for no network permission and needs none: nothing to update from.
        disable += "GradleDependency"
        disable += "NewerVersionAvailable"
        disable += "AndroidGradlePluginVersion"
    }
}

// Said once, at the top of a release build, rather than discovered later when
// the APK will not install.
if (!canSignRelease && gradle.startParameter.taskNames.any { it.contains("elease") }) {
    logger.lifecycle(
        "Waymark: no release keystore configured — this build will be UNSIGNED " +
            "and cannot be installed. See RELEASING.md."
    )
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.notifications)
    implementation(projects.feature.shelf)
    implementation(projects.feature.people)
    implementation(projects.feature.share)
    implementation(projects.feature.flight)
    implementation(projects.feature.trip)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)

    baselineProfile(projects.baselineprofile)
}
