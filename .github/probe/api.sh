#!/usr/bin/env bash
# Prints the newest stable versions and the public API of the libraries the
# next changes use. Temporary.
set -u
G=https://dl.google.com/android/maven2; C=https://repo1.maven.org/maven2
latest() { curl -sfL "$1/${2//.//}/$3/maven-metadata.xml" | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' | grep -viE 'alpha|beta|rc|dev|-m[0-9]' | sort -V | tail -1; }
src() { # repo group artifact version -> dir
  local d=/tmp/src/$3; mkdir -p $d
  for a in $3-android $3; do
    curl -sfL -o $d.jar "$1/${2//.//}/$a/$4/$a-$4-sources.jar" && { unzip -qo $d.jar -d $d; break; }
  done; echo $d; }
for a in androidx.navigation3:navigation3-ui androidx.compose.material3.adaptive:adaptive-navigation3 androidx.hilt:hilt-lifecycle-viewmodel-compose androidx.hilt:hilt-navigation-compose androidx.test.uiautomator:uiautomator androidx.test:runner androidx.benchmark:benchmark-macro-junit4 androidx.lifecycle:lifecycle-viewmodel-navigation3; do echo "V $a $(latest $G ${a%%:*} ${a##*:})"; done
for a in com.google.dagger:hilt-android com.google.dagger:hilt-android-gradle-plugin io.github.takahirom.roborazzi:roborazzi-compose; do echo "V $a $(latest $C ${a%%:*} ${a##*:})"; done
sig() { grep -rhoE "^(public |@Composable )*(fun|class|interface|object|data class|sealed interface|sealed class|value class|typealias|val|var) [^=]*" "$@" | cut -c1-220 | sort -u; }
echo "=== nav3 runtime"; d=$(src $G androidx.navigation3 navigation3-runtime 1.2.0); sig $d | head -80
echo "=== nav3 ui"; d=$(src $G androidx.navigation3 navigation3-ui 1.2.0); sig $d | head -80
grep -rn -A30 "^public fun <T : Any> NavDisplay(" $d | head -60
echo "=== lvm nav3"; d=$(src $G androidx.lifecycle lifecycle-viewmodel-navigation3 2.11.0); sig $d | head -20
v=$(latest $G androidx.compose.material3.adaptive adaptive-navigation3); echo "=== adaptive nav3 $v"; d=$(src $G androidx.compose.material3.adaptive adaptive-navigation3 $v); sig $d | head -40
v=$(latest $G androidx.hilt hilt-lifecycle-viewmodel-compose); echo "=== hilt vm compose $v"; d=$(src $G androidx.hilt hilt-lifecycle-viewmodel-compose $v); grep -rn -B2 -A12 "fun <.*hiltViewModel" $d | head -60
echo "=== glance"; d=$(src $G androidx.glance glance-appwidget 1.2.0); grep -rn "fun providePreview\|fun setWidgetPreviews\|val previewSizeMode\|class SizeMode\|object Responsive\|class Responsive\|LocalSize\b" $d | head -20
echo "=== core notifications"; d=$(src $G androidx.core core 1.19.1); grep -rn "public @NonNull Builder setRequestPromotedOngoing\|setShortCriticalText\|public static class ProgressStyle\|public @NonNull ProgressStyle set[A-Za-z]*(\|public Segment(\|public Point(\|canPostPromotedNotifications\|POST_PROMOTED" $d | cut -c1-200 | head -40
echo "=== benchmark"; d=$(src $G androidx.benchmark benchmark-macro-junit4 1.5.0); grep -rn "fun collect(" -A12 $d | head -30
