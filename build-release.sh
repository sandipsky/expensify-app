#!/usr/bin/env bash
# Builds the signed release APK of Expensify for installing on your own phone (no Play Store).
# Run with --help for details.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./build-release.sh [--install] [--clean]

Builds the signed, R8-shrunk release APK and copies it to dist/expensify-<version>-release.apk.

  --install   after building, install it on the phone connected over USB (adb install -r)
  --clean     delete previous build outputs first
  --help      this text

Needs on this machine (all git-ignored; see CLAUDE.md "State of the repo"):
  keystore.properties                   storeFile / storePassword / keyAlias / keyPassword
  app/release.keystore                  the keystore that keystore.properties points at
  app/src/release/google-services.json  the Android app's config from Firebase project expensify-13477
  an Android SDK                        local.properties (sdk.dir=...), $ANDROID_HOME, or ~/Android/Sdk
  a JDK to start Gradle                 java on PATH or $JAVA_HOME, 17 or newer (the build's own JDK 25
                                        is downloaded by Gradle the first time)

Back up app/release.keystore and keystore.properties: a phone only accepts an update that is
signed with the same key as the app already installed.
EOF
}

fail() { printf '\nerror: %s\n' "$*" >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }

cd "$(dirname "$0")"
root="$(pwd)"

install=0
clean=0
for arg in "$@"; do
  case "$arg" in
    --install) install=1 ;;
    --clean)   clean=1 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n\n' "$arg" >&2; usage >&2; exit 2 ;;
  esac
done

# Reads key=value from a Java .properties file, undoing the \\ and \: escapes that paths carry.
prop() {
  sed -n "s/^[[:space:]]*$2[[:space:]]*=[[:space:]]*//p" "$1" | head -n 1 | tr -d '\r' \
    | sed -e 's/\\\\/\\/g' -e 's/\\:/:/g'
}

# --- Preflight -------------------------------------------------------------------------------

step "Checking signing, Firebase config, SDK and JDK"

[ -f keystore.properties ] || fail "keystore.properties is missing at the repo root.
If you never made a keystore, create one (and back it up):
  keytool -genkeypair -v -keystore app/release.keystore -alias expensify -keyalg RSA -keysize 2048 -validity 10000
then write keystore.properties:
  storeFile=app/release.keystore
  storePassword=<the store password>
  keyAlias=expensify
  keyPassword=<the key password>"

store_file="$(prop keystore.properties storeFile)"
[ -n "$store_file" ] || fail "keystore.properties has no storeFile= line."
case "$store_file" in /*) ;; *) store_file="$root/$store_file" ;; esac
[ -f "$store_file" ] || fail "Keystore not found: $store_file (storeFile in keystore.properties)."
for key in storePassword keyAlias keyPassword; do
  [ -n "$(prop keystore.properties "$key")" ] || fail "keystore.properties has no $key= line."
done

[ -f app/src/release/google-services.json ] || fail "app/src/release/google-services.json is missing.
Fetch it from the Firebase project (needs the Firebase CLI, logged in):
  firebase apps:list --project expensify-13477          # prints the Android app id
  firebase apps:sdkconfig ANDROID <appId> --project expensify-13477 -o app/src/release/google-services.json"

sdk_dir=""
if [ -f local.properties ]; then
  sdk_dir="$(prop local.properties sdk.dir)"
  [ -z "$sdk_dir" ] || [ -d "$sdk_dir" ] || fail "local.properties points at an SDK that isn't there: $sdk_dir"
fi
if [ -z "$sdk_dir" ]; then
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk"; do
    if [ -n "$candidate" ] && [ -d "$candidate" ]; then sdk_dir="$candidate"; break; fi
  done
fi
[ -n "$sdk_dir" ] || fail "No Android SDK found. Install one (Android Studio, or the command-line tools) and either
set ANDROID_HOME=/path/to/Android/Sdk or write local.properties with sdk.dir=/path/to/Android/Sdk."
export ANDROID_HOME="$sdk_dir"

if [ -n "${JAVA_HOME:-}" ]; then
  [ -x "$JAVA_HOME/bin/java" ] || fail "JAVA_HOME is set but $JAVA_HOME/bin/java doesn't exist."
elif ! command -v java >/dev/null 2>&1; then
  for candidate in /opt/android-studio/jbr "$HOME/android-studio/jbr" /usr/local/android-studio/jbr \
                   /snap/android-studio/current/jbr; do
    if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
  done
  [ -n "${JAVA_HOME:-}" ] || fail "No JDK found. Install one (JDK 17 or newer, e.g. apt install openjdk-21-jdk) or set JAVA_HOME;
Android Studio's bundled one works too (<android-studio>/jbr)."
fi

printf 'signing   %s\n' "$store_file"
printf 'firebase  app/src/release/google-services.json\n'
printf 'sdk       %s\n' "$sdk_dir"
printf 'jdk       %s\n' "${JAVA_HOME:-$(command -v java)}"

# --- Build -----------------------------------------------------------------------------------

tasks=(assembleRelease)
if [ "$clean" = 1 ]; then tasks=(clean assembleRelease); fi
step "Building the signed release APK (gradle ${tasks[*]})"
# `sh ./gradlew` rather than ./gradlew: the wrapper is tracked without the executable bit.
sh ./gradlew "${tasks[@]}"

apk=app/build/outputs/apk/release/app-release.apk
if [ ! -f "$apk" ]; then
  if [ -f app/build/outputs/apk/release/app-release-unsigned.apk ]; then
    fail "Gradle produced an unsigned APK, so the release signingConfig didn't apply. Check keystore.properties."
  fi
  fail "Build finished but $apk is missing."
fi

version="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
code="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
out="dist/expensify-${version:-release}-release.apk"
mkdir -p dist
cp -f "$apk" "$out"
size="$(du -h "$out" | cut -f1)"
step "Done: $out  (version ${version:-?}, code ${code:-?}, $size)"

if [ "$install" = 0 ]; then
  cat <<EOF
To put it on your phone:
  - copy $out to the phone (USB cable, Nearby Share, Drive, email, ...), open it in the Files app
    and allow "Install unknown apps" for that app when Android asks; or
  - turn on USB debugging, plug the phone in, and run:  ./build-release.sh --install
EOF
  exit 0
fi

# --- Install over adb ------------------------------------------------------------------------

step "Installing on the phone over adb"
adb="$sdk_dir/platform-tools/adb"
if [ ! -x "$adb" ]; then adb="$(command -v adb || true)"; fi
[ -n "$adb" ] || fail "adb not found at $sdk_dir/platform-tools/adb and not on PATH. Install Android SDK Platform-Tools."

device_list="$("$adb" devices)"
devices="$(printf '%s\n' "$device_list" | awk 'NR > 1 && $2 == "device" { print $1 }')"
count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"
if [ "$count" -eq 0 ]; then
  fail "No phone found. Turn on USB debugging (Settings > Developer options), plug it in, tap Allow on the phone, then retry.
adb says:
$device_list"
fi
if [ "$count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
  fail "More than one device is connected. Pick one: ANDROID_SERIAL=<serial> ./build-release.sh --install (serials: adb devices)."
fi

if ! "$adb" install -r "$out"; then
  fail "adb install failed. If it said INSTALL_FAILED_UPDATE_INCOMPATIBLE, the phone has an Expensify signed with a different key:
  $adb uninstall com.sandipshakya.expensify     (removes that install and its on-device settings; your data is in Firestore)
then run this again."
fi
step "Installed com.sandipshakya.expensify. Open Expensify on the phone."
