@echo off
rem Starts the Indicator Profile Builder and opens http://127.0.0.1:8765 in the browser.
rem Profiles are stored in a "data" folder next to the jar.
rem   run.cmd                 normal use
rem   run.cmd --simulator 9001    also start a SIMULATED indicator on TCP 127.0.0.1:9001 (training only)
setlocal
set "HERE=%~dp0"
if exist "%HERE%target\indicator-profile-builder.jar" (
  set "JAR=%HERE%target\indicator-profile-builder.jar"
) else (
  set "JAR=%HERE%indicator-profile-builder.jar"
)
java -jar "%JAR%" %*
endlocal
