#!/usr/bin/env bash
set -u
G=https://dl.google.com/android/maven2
C=https://repo1.maven.org/maven2
src() { local d=/tmp/src/$3; mkdir -p $d
  for a in $3-android $3; do curl -sfL -o $d.jar "$1/${2//.//}/$a/$4/$a-$4-sources.jar" && { unzip -qo $d.jar -d $d; break; }; done; echo $d; }
pk() { for f in $(grep -rl "$2" $1); do echo "== ${f#/tmp/src/}: $(grep -m1 '^package' $f)"; done; }
show() { grep -rn -A${3:-14} "$2" $1 | sed 's#^/tmp/src/##' | cut -c1-170 | head -${4:-40}; }
d=$(src $G androidx.navigation3 navigation3-runtime 1.2.0)
pk $d "fun rememberSaveableStateHolderNavEntryDecorator\|fun rememberNavBackStack\|fun <T : Any> entryProvider\|interface NavKey\|class NavBackStack"
d=$(src $G androidx.navigation3 navigation3-ui 1.2.0)
pk $d "^public fun <T : Any> NavDisplay("
d=$(src $G androidx.lifecycle lifecycle-viewmodel-navigation3 2.11.0)
pk $d "fun <T : Any> rememberViewModelStoreNavEntryDecorator("
show $d "HasDefaultViewModelProviderFactory" 3 30
d=$(src $G androidx.hilt hilt-lifecycle-viewmodel-compose 1.4.0)
pk $d "fun <reified VM : ViewModel> hiltViewModel"
show $d "fun createHiltViewModelFactory" 12 20
d=$(src $C com.google.dagger hilt-android 2.60.1)
show $d "public ViewModel create(" 40 70
curl -sfL "$C/com/google/dagger/hilt-android-gradle-plugin/2.60.1/hilt-android-gradle-plugin-2.60.1.pom" | grep -B1 -A3 "<artifactId>gradle\|<artifactId>gradle-api" | head -20
for a in roborazzi roborazzi-compose roborazzi-junit-rule roborazzi-gradle-plugin; do echo "$a: $(curl -s -o /dev/null -w '%{http_code}' $C/io/github/takahirom/roborazzi/$a/1.77.0/$a-1.77.0.pom)"; done
d=$(src $C io.github.takahirom.roborazzi roborazzi-compose 1.77.0)
show $d "^fun captureRoboImage(" 8 30
d=$(src $G androidx.benchmark benchmark-macro 1.5.0)
show $d "^public fun MacrobenchmarkScope.startActivityAndWait\|public fun startActivityAndWait(" 3 12
show $d "public fun measureRepeated(" 14 20
