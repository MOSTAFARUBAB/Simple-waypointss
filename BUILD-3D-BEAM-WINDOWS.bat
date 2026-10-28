@echo off
setlocal
cd /d "%~dp0"
echo ================================================
echo Simple Waypoints - Vanilla Beacon Beam Build
echo Minecraft 26.3 / Fabric / Java 25
echo ================================================
echo.
where java >nul 2>nul || (echo ERROR: Java is not installed or not on PATH.& echo Install Java 25, then try again.& pause & exit /b 1)
java -version
where gradle >nul 2>nul
if errorlevel 1 (
  if exist "gradlew.bat" (
    set "GRADLE_CMD=gradlew.bat"
  ) else (
    echo ERROR: Gradle was not found and no Gradle wrapper is included.
    echo Open this folder in your IDE or install Gradle 9.6+, then run this file again.
    pause
    exit /b 1
  )
) else (
  set "GRADLE_CMD=gradle"
)
echo.
echo Building the 3D vanilla-beacon beam variant...
call %GRADLE_CMD% clean build -PwithBeam=true
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the error text and send it to ChatGPT.
  pause
  exit /b 1
)
if not exist "build\libs" (
  echo ERROR: build\libs was not created.
  pause
  exit /b 1
)
if not exist "READY-TO-INSTALL" mkdir "READY-TO-INSTALL"
set "FOUND="
for %%F in (build\libs\*-3d-beam-*.jar) do (
  echo %%~nxF | findstr /i /v "sources dev" >nul
  if not errorlevel 1 (
    copy /y "%%F" "READY-TO-INSTALL\%%~nxF" >nul
    set "FOUND=%%~nxF"
  )
)
if not defined FOUND (
  echo Build succeeded, but I could not identify the normal 3D-beam JAR.
  echo Check build\libs manually and send a screenshot if needed.
  pause
  exit /b 1
)
echo.
echo SUCCESS! Your installable mod is:
echo %CD%\READY-TO-INSTALL\%FOUND%
echo.
echo Copy ONLY this JAR into your Minecraft mods folder.
echo Remove older simple-waypoints JARs first to avoid duplicates.
pause
endlocal
