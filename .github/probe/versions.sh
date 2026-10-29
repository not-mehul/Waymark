#!/usr/bin/env bash
# Prints the newest stable release of each dependency Waymark uses or might.
set -u
google=https://dl.google.com/android/maven2
central=https://repo1.maven.org/maven2
latest() { # repo group artifact
  local path="${2//.//}/$3/maven-metadata.xml"
  curl -sfL "$1/$path" | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' \
    | grep -viE 'alpha|beta|rc|dev|eap|-m[0-9]|snapshot|pre' | sort -V | tail -1
}
for a in \
  com.android.tools.build:gradle androidx.compose:compose-bom androidx.core:core-ktx \
  androidx.lifecycle:lifecycle-runtime-ktx androidx.activity:activity-compose \
  androidx.navigation:navigation-compose androidx.navigation3:navigation3-runtime \
  androidx.room:room-runtime androidx.work:work-runtime-ktx androidx.profileinstaller:profileinstaller \
  androidx.test:core-ktx androidx.test.ext:junit-ktx androidx.glance:glance-appwidget \
  androidx.datastore:datastore-preferences androidx.core:core-splashscreen \
  androidx.compose.material3.adaptive:adaptive androidx.compose.material3:material3 \
  androidx.compose.material3.adaptive:adaptive-layout androidx.compose.material3:material3-adaptive-navigation-suite \
  androidx.benchmark:benchmark-macro-junit4 androidx.baselineprofile:androidx.baselineprofile.gradle.plugin \
  androidx.sqlite:sqlite-bundled androidx.room:androidx.room.gradle.plugin androidx.compose.ui:ui \
  androidx.lifecycle:lifecycle-viewmodel-navigation3 androidx.activity:activity-ktx; do
  echo "$a $(latest $google ${a%%:*} ${a##*:})"
done
for a in \
  org.jetbrains.kotlin:kotlin-gradle-plugin com.google.devtools.ksp:symbol-processing-gradle-plugin \
  org.jetbrains.kotlinx:kotlinx-serialization-json org.jetbrains.kotlinx:kotlinx-coroutines-core \
  org.robolectric:robolectric com.google.zxing:core junit:junit \
  com.pinterest.ktlint:ktlint-cli io.gitlab.arturbosch.detekt:detekt-cli dev.detekt:detekt-cli \
  io.github.takahirom.roborazzi:roborazzi org.jetbrains.kotlinx:kotlinx-datetime \
  app.cash.turbine:turbine com.google.truth:truth; do
  echo "$a $(latest $central ${a%%:*} ${a##*:})"
done
echo "gradle $(curl -sfL https://services.gradle.org/versions/current | grep -o '"version" *: *"[^"]*"' | head -1)"
for r in actions/checkout actions/setup-java actions/upload-artifact gradle/actions softprops/action-gh-release; do
  echo "$r $(curl -sfL https://api.github.com/repos/$r/releases/latest | grep -o '"tag_name": *"[^"]*"' | head -1)"
done
# What AGP needs: the compatibility table it ships.
curl -sfL "$google/com/android/tools/build/gradle/$(latest $google com.android.tools.build gradle)/gradle-$(latest $google com.android.tools.build gradle).pom" | grep -A3 -E "artifactId>(kotlin|builder)" | head -30
