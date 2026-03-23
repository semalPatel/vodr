#!/usr/bin/env sh

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
export GRADLE_USER_HOME="$DIR/.gradle"
JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [ -z "${JAVA_CMD}" ]; then
  JAVA_CMD=java
fi
exec "$JAVA_CMD" \
  -Dorg.gradle.appname=gradlew \
  -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
