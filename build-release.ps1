<#
.SYNOPSIS
    Builds the signed release APK of Expensify for installing on your own phone (no Play Store).

.DESCRIPTION
    Runs the preflight checks, builds the signed, R8-shrunk release APK with Gradle and copies it
    to dist\expensify-<version>-release.apk. With -Install it then pushes it to the phone connected
    over USB with adb.

    Needs on this machine (all git-ignored; see CLAUDE.md "State of the repo"):
      keystore.properties                   storeFile / storePassword / keyAlias / keyPassword
      app\release.keystore                  the keystore that keystore.properties points at
      app\src\release\google-services.json  the Android app's config from Firebase project expensify-13477
      an Android SDK                        local.properties (sdk.dir=...), ANDROID_HOME, or %LOCALAPPDATA%\Android\Sdk
      a JDK to start Gradle                 java on PATH, JAVA_HOME, or Android Studio's bundled jbr; 17 or newer
                                            (the build's own JDK 25 is downloaded by Gradle the first time)

    Back up app\release.keystore and keystore.properties: a phone only accepts an update that is
    signed with the same key as the app already installed.

.PARAMETER Install
    After building, install the APK on the phone connected over USB (adb install -r).

.PARAMETER Clean
    Delete previous build outputs first.

.EXAMPLE
    .\build-release.ps1

.EXAMPLE
    .\build-release.ps1 -Install

.EXAMPLE
    build-release.cmd -Install
    The same from cmd.exe, or when PowerShell's execution policy refuses to run .ps1 files.
#>
[CmdletBinding()]
param(
    [switch]$Install,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$root = $PSScriptRoot

function Fail {
    param([string]$Message)
    Write-Host ''
    Write-Host "error: $Message" -ForegroundColor Red
    exit 1
}

function Step {
    param([string]$Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

# Reads key=value from a Java .properties file, undoing the \\ and \: escapes that paths carry.
function Read-Prop {
    param([string]$File, [string]$Key)
    $pattern = '^\s*' + [regex]::Escape($Key) + '\s*=\s*(.*)$'
    foreach ($line in Get-Content -LiteralPath $File) {
        if ($line -match $pattern) {
            return ($Matches[1].Trim() -replace '\\\\', '\' -replace '\\:', ':')
        }
    }
    return ''
}

# Runs a program, shows its output and returns its exit code without PowerShell turning a
# non-zero exit or a line on stderr into an exception (behaviour differs between 5.1 and 7.x).
function Invoke-Tool {
    param([string]$Exe, [string[]]$Arguments)
    $ErrorActionPreference = 'Continue'
    & $Exe @Arguments | Out-Host
    return $LASTEXITCODE
}

Push-Location -LiteralPath $root
try {
    # --- Preflight ---------------------------------------------------------------------------

    Step 'Checking signing, Firebase config, SDK and JDK'

    if (-not (Test-Path -LiteralPath 'keystore.properties')) {
        Fail @"
keystore.properties is missing at the repo root.
If you never made a keystore, create one (and back it up):
  keytool -genkeypair -v -keystore app\release.keystore -alias expensify -keyalg RSA -keysize 2048 -validity 10000
then write keystore.properties:
  storeFile=app/release.keystore
  storePassword=<the store password>
  keyAlias=expensify
  keyPassword=<the key password>
"@
    }

    $storeFile = Read-Prop 'keystore.properties' 'storeFile'
    if (-not $storeFile) { Fail 'keystore.properties has no storeFile= line.' }
    if (-not [System.IO.Path]::IsPathRooted($storeFile)) { $storeFile = Join-Path $root $storeFile }
    if (-not (Test-Path -LiteralPath $storeFile)) { Fail "Keystore not found: $storeFile (storeFile in keystore.properties)." }
    foreach ($key in 'storePassword', 'keyAlias', 'keyPassword') {
        if (-not (Read-Prop 'keystore.properties' $key)) { Fail "keystore.properties has no $key= line." }
    }

    if (-not (Test-Path -LiteralPath 'app\src\release\google-services.json')) {
        Fail @"
app\src\release\google-services.json is missing.
Fetch it from the Firebase project (needs the Firebase CLI, logged in):
  firebase apps:list --project expensify-13477          # prints the Android app id
  firebase apps:sdkconfig ANDROID <appId> --project expensify-13477 -o app/src/release/google-services.json
"@
    }

    $sdkDir = ''
    if (Test-Path -LiteralPath 'local.properties') {
        $sdkDir = Read-Prop 'local.properties' 'sdk.dir'
        if ($sdkDir -and -not (Test-Path -LiteralPath $sdkDir)) {
            Fail "local.properties points at an SDK that isn't there: $sdkDir"
        }
    }
    if (-not $sdkDir) {
        foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
            if ($candidate -and (Test-Path -LiteralPath $candidate)) { $sdkDir = $candidate; break }
        }
    }
    if (-not $sdkDir) {
        Fail 'No Android SDK found. Install one (Android Studio, or the command-line tools) and either set ANDROID_HOME or write local.properties with sdk.dir=C:\\path\\to\\Android\\Sdk.'
    }
    $env:ANDROID_HOME = $sdkDir

    if ($env:JAVA_HOME) {
        if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
            Fail "JAVA_HOME is set but $env:JAVA_HOME\bin\java.exe doesn't exist."
        }
        $jdk = $env:JAVA_HOME
    } elseif (Get-Command java -ErrorAction SilentlyContinue) {
        $jdk = (Get-Command java).Source
    } else {
        $jbr = @("$env:ProgramFiles\Android\Android Studio\jbr", "$env:LOCALAPPDATA\Programs\Android Studio\jbr") |
            Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe') } |
            Select-Object -First 1
        if (-not $jbr) {
            Fail "No JDK found. Install one (JDK 17 or newer) or set JAVA_HOME; Android Studio's bundled one works too (...\Android Studio\jbr)."
        }
        $env:JAVA_HOME = $jbr
        $jdk = $jbr
    }

    Write-Host "signing   $storeFile"
    Write-Host 'firebase  app\src\release\google-services.json'
    Write-Host "sdk       $sdkDir"
    Write-Host "jdk       $jdk"

    # --- Build -------------------------------------------------------------------------------

    $tasks = @('assembleRelease')
    if ($Clean) { $tasks = @('clean') + $tasks }
    Step "Building the signed release APK (gradlew $($tasks -join ' '))"
    $code = Invoke-Tool (Join-Path $root 'gradlew.bat') $tasks
    if ($code -ne 0) { Fail "Gradle failed (exit code $code); see the output above." }

    $apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $apk)) {
        if (Test-Path -LiteralPath (Join-Path $root 'app\build\outputs\apk\release\app-release-unsigned.apk')) {
            Fail "Gradle produced an unsigned APK, so the release signingConfig didn't apply. Check keystore.properties."
        }
        Fail "Build finished but $apk is missing."
    }

    $gradleFile = Get-Content -LiteralPath 'app\build.gradle.kts' -Raw
    $versionName = if ($gradleFile -match 'versionName\s*=\s*"([^"]+)"') { $Matches[1] } else { 'release' }
    $versionCode = if ($gradleFile -match 'versionCode\s*=\s*(\d+)') { $Matches[1] } else { '?' }
    $outName = "expensify-$versionName-release.apk"
    $out = Join-Path $root "dist\$outName"
    New-Item -ItemType Directory -Force -Path (Join-Path $root 'dist') | Out-Null
    Copy-Item -LiteralPath $apk -Destination $out -Force
    $sizeMb = [math]::Round((Get-Item -LiteralPath $out).Length / 1MB, 1)
    Step "Done: dist\$outName  (version $versionName, code $versionCode, $sizeMb MB)"

    if (-not $Install) {
        Write-Host @"
To put it on your phone:
  - copy dist\$outName to the phone (USB cable, Nearby Share, Drive, email, ...), open it in the
    Files app and allow "Install unknown apps" for that app when Android asks; or
  - turn on USB debugging, plug the phone in, and run:  .\build-release.ps1 -Install
"@
        exit 0
    }

    # --- Install over adb --------------------------------------------------------------------

    Step 'Installing on the phone over adb'
    $adb = Join-Path $sdkDir 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adb)) {
        $found = Get-Command adb -ErrorAction SilentlyContinue
        if (-not $found) { Fail "adb not found at $adb and not on PATH. Install Android SDK Platform-Tools (SDK Manager)." }
        $adb = $found.Source
    }

    $ErrorActionPreference = 'Continue'   # adb prints its daemon start-up notes on stderr
    $deviceLines = @(& $adb devices | ForEach-Object { "$_" })
    $ErrorActionPreference = 'Stop'
    $devices = @($deviceLines | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })

    if ($devices.Count -eq 0) {
        Fail @"
No phone found. Turn on USB debugging (Settings > Developer options), plug it in, tap Allow on the phone, then retry.
adb says:
$($deviceLines -join "`n")
"@
    }
    if ($devices.Count -gt 1 -and -not $env:ANDROID_SERIAL) {
        Fail "More than one device is connected. Pick one by setting `$env:ANDROID_SERIAL = '<serial>' first (serials: adb devices)."
    }

    $code = Invoke-Tool $adb @('install', '-r', $out)
    if ($code -ne 0) {
        Fail @"
adb install failed. If it said INSTALL_FAILED_UPDATE_INCOMPATIBLE, the phone has an Expensify signed with a different key:
  adb uninstall com.sandipshakya.expensify     (removes that install and its on-device settings; your data is in Firestore)
then run this again.
"@
    }
    Step 'Installed com.sandipshakya.expensify. Open Expensify on the phone.'
} finally {
    Pop-Location
}
