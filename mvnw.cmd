@ECHO OFF
setlocal

set BASEDIR=%~dp0
set WRAPPER_DIR=%BASEDIR%\.mvn\wrapper
set WRAPPER_PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties
set WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar

if not exist "%WRAPPER_DIR%" (
  mkdir "%WRAPPER_DIR%"
)

set WRAPPER_URL=
if exist "%WRAPPER_PROPERTIES%" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPERTIES%") do (
    if /i "%%A"=="wrapperUrl" set WRAPPER_URL=%%B
  )
)

if "%WRAPPER_URL%"=="" set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar

if not exist "%WRAPPER_JAR%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%' -UseBasicParsing } catch { Write-Error $_; exit 1 }"
  if errorlevel 1 (
    echo Failed to download Maven Wrapper jar.
    exit /b 1
  )
)

if not "%JAVA_HOME%"=="" (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java
)

set MAVEN_PROJECTBASEDIR=%BASEDIR:~0,-1%

"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
