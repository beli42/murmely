#!/usr/bin/env bash
# Runs the emulator tests twice: in portrait, then in landscape (the screens are laid out
# differently, see OrientationListener). The video encoder tests don't depend on the screen and
# run once. Reports: build/reports/androidTests-portrait/ and -landscape/, per module.
#
# Usage: scripts/connected-tests.sh [more Gradle options, e.g. --no-daemon]
set -u
cd "$(dirname "$0")/.."

modules=(app media-editor/photoEditor media-editor/videoEditor)
failed=0

keep_reports() {
    for m in "${modules[@]}"; do
        if [ -d "$m/build/reports/androidTests/connected" ]; then
            mkdir -p "build/reports/androidTests-$1/$m"
            cp -r "$m/build/reports/androidTests/connected/." "build/reports/androidTests-$1/$m/"
        fi
    done
}

rm -rf build/reports/androidTests-portrait build/reports/androidTests-landscape

echo "== Portrait"
./gradlew :app:connectedDebugAndroidTest :photoEditor:connectedDebugAndroidTest :videoEditor:connectedDebugAndroidTest \
    --continue -Pandroid.testInstrumentationRunnerArguments.orientation=portrait "$@" || failed=1
keep_reports portrait

echo "== Landscape"
./gradlew :app:connectedDebugAndroidTest :photoEditor:connectedDebugAndroidTest \
    --continue -Pandroid.testInstrumentationRunnerArguments.orientation=landscape "$@" || failed=1
keep_reports landscape

exit $failed
