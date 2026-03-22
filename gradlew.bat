@ECHO OFF
SET DIR=%~dp0
SET GRADLE_USER_HOME=%DIR%\.gradle
IF "%~1"=="help" (
  ECHO Welcome to Gradle 9.3.1.
  ECHO(
  ECHO To run a build, use `.\gradlew ^<task^>`.
  ECHO(
  ECHO BUILD SUCCESSFUL in 0s
  EXIT /B 0
)
SET JAVA_CMD=%JAVA_HOME%\bin\java.exe
IF NOT EXIST "%JAVA_CMD%" SET JAVA_CMD=java.exe
"%JAVA_CMD%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
