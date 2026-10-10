#!/usr/bin/env bash
# Opens the app on the CI emulator, presses the power button, switches modes
# and opens the screen light. The screenshot it takes is the one the README
# shows.
#
# A file rather than inline workflow script: the emulator action feeds inline
# scripts to `sh -c` one line at a time, which breaks every multi-line `if`.
set -x

PKG=no.mwmai.flashlight.debug
ACTIVITY=no.mwmai.flashlight.MainActivity
OUT=smoke
mkdir -p "$OUT"

chmod +x ./gradlew
./gradlew installDebug --no-daemon || { echo "::error::install failed"; exit 1; }

adb logcat -c || true
# No -W: it waits for a launch to settle, which never happens if the app dies.
adb shell am start -n "$PKG/$ACTIVITY" || echo "am start returned $?"
sleep 12

dump() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/ui.xml "$OUT/$1" >/dev/null 2>&1 || true
}
crashed() {
    adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
    if grep -qE "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"; then
        echo "::error::App crashed"
        grep -B 2 -A 45 -m 1 -E "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"
        return 0
    fi
    return 1
}
tap() {
    python3 tools/ui_center.py "$OUT/$1" "$2" > "$OUT/tap.txt" || { echo "::error::nothing to tap for: $2"; exit 1; }
    # shellcheck disable=SC2046
    adb shell input tap $(cat "$OUT/tap.txt")
}
need() {
    if ! grep -qF "$2" "$OUT/$1"; then
        echo "::error::$3 (missing text: $2)"
        head -c 6000 "$OUT/$1"
        exit 1
    fi
}

dump ui_launch.xml
crashed && exit 1
for TEXT in "MWM FLASHLIGHT" "OFF" "STEADY" "STROBE" "SOS" "SCREEN LIGHT" "Power"; do
    need ui_launch.xml "$TEXT" "The main screen is incomplete"
done

if grep -qF 'NO FLASH:' "$OUT/ui_launch.xml"; then
    # No flash unit on this emulator image: power opens the screen light.
    echo "::warning::emulator reports no flash, testing the screen-light path only"
    adb exec-out screencap -p > "$OUT/flashlight.png" 2>/dev/null || true
    tap ui_launch.xml "desc=Power"
    sleep 3
    dump ui_power.xml
    crashed && exit 1
    need ui_power.xml "TAP TO CLOSE" "Power did not open the screen light on a phone without flash"
else
    tap ui_launch.xml "desc=Power"
    sleep 3
    dump ui_on.xml
    crashed && exit 1
    need ui_on.xml 'text="ON"' "Tapped power but the display never said ON"
    adb exec-out screencap -p > "$OUT/flashlight.png" 2>/dev/null || true

    tap ui_on.xml "desc=SOS"
    sleep 3
    dump ui_sos.xml
    crashed && exit 1
    need ui_sos.xml "SIGNALLING SOS" "SOS mode did not start"

    tap ui_sos.xml "desc=Power"
    sleep 2
    dump ui_off.xml
    need ui_off.xml 'text="OFF"' "Power did not switch the light off"
    tap ui_off.xml "desc=STEADY"
    sleep 1
    dump ui_steady.xml
    tap ui_steady.xml "desc=SCREEN LIGHT"
    sleep 3
    dump ui_power.xml
    crashed && exit 1
    need ui_power.xml "TAP TO CLOSE" "SCREEN LIGHT did not open the screen light"
fi

tap ui_power.xml "desc=Screen light"
sleep 2
dump ui_closed.xml
crashed && exit 1
need ui_closed.xml "MWM FLASHLIGHT" "Tapping the screen light did not close it"
echo "Power, modes and screen light all respond."
