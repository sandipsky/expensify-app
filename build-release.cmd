@echo off
rem Windows entry point: runs build-release.ps1 even when PowerShell's execution policy blocks .ps1 files.
rem   build-release.cmd              build  ->  dist\expensify-<version>-release.apk
rem   build-release.cmd -Install     build, then install on the phone connected over USB
rem   build-release.cmd -Clean       delete previous build outputs first
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-release.ps1" %*
exit /b %ERRORLEVEL%
