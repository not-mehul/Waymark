#!/usr/bin/env bash
set -u
G=https://dl.google.com/android/maven2
src() { local d=/tmp/src/$3; mkdir -p $d
  for a in $3-android $3; do curl -sfL -o $d.jar "$1/${2//.//}/$a/$4/$a-$4-sources.jar" && { unzip -qo $d.jar -d $d; break; }; done; echo $d; }
show() { grep -rn -A${3:-14} "$2" $1 | sed 's#^/tmp/src/##' | cut -c1-170 | head -${4:-40}; }
d=$(src $G androidx.navigation3 navigation3-runtime 1.2.0)
show $d "^public fun rememberNavBackStack(" 6 30
show $d "fun <T : Any> entryProvider(" 8 20
show $d "public inline fun <reified K : T> entry(" 10 30
show $d "^public class NavEntry<T : Any>(" 8 12
d=$(src $G androidx.lifecycle lifecycle-viewmodel-navigation3 2.11.0)
show $d "public fun <T : Any> rememberViewModelStoreNavEntryDecorator(" 8 12
d=$(src $G androidx.compose.material3.adaptive adaptive-navigation3 1.3.0)
show $d "public fun <T : Any> rememberListDetailSceneStrategy(" 10 14
show $d "public fun listPane(\|public fun detailPane(\|public fun extraPane(" 6 40
d=$(src $G androidx.glance glance-appwidget 1.2.0)
show $d "public suspend fun setWidgetPreviews(" 12 16
d=$(src $G androidx.baselineprofile androidx.baselineprofile.gradle.plugin 1.5.0)
curl -sfL "$G/androidx/baselineprofile/androidx.baselineprofile.gradle.plugin/1.5.0/androidx.baselineprofile.gradle.plugin-1.5.0.pom" | grep -A2 artifactId | head
