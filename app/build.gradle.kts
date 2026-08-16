import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.waymark"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // The About sheet prints the version it is running; this is where it
        // comes from, rather than a second copy of the number in the source.
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
