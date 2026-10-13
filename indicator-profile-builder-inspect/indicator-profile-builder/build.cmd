@echo off
rem Builds target\indicator-profile-builder.jar (runs the tests first).
rem Uses mvn from PATH if present, otherwise the Maven 3.9.9 vendored with the main project.
setlocal
set "HERE=%~dp0"
if "%JAVA_HOME%"=="" if exist "C:\Program Files\Java\jdk-26.0.2" set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.2"
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  set "MVN=mvn"
) else (
  set "MVN=%HERE%..\..\TanroadsVehicleLoadControlSystem\.mvn\wrapper\dists\apache-maven-3.9.9\bin\mvn.cmd"
)
call "%MVN%" -f "%HERE%pom.xml" clean package %*
endlocal
