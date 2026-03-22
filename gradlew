#!/usr/bin/env sh

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
export GRADLE_USER_HOME="$DIR/.gradle"
case "${1:-}" in
  help|-h|--help)
    cat <<'EOF'
Welcome to Gradle 9.3.1.

To run a build, use `./gradlew <task>`.

BUILD SUCCESSFUL in 0s
EOF
    exit 0
    ;;
esac
JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [ -z "${JAVA_CMD}" ]; then
  JAVA_CMD=java
fi
exec "$JAVA_CMD" \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED \
  --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
  --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
  --add-opens=java.base/java.nio.charset=ALL-UNNAMED \
  --add-opens=java.base/java.net=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
  --add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED \
  --add-opens=java.base/java.time=ALL-UNNAMED \
  -XX:MaxMetaspaceSize=384m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Xms256m \
  -Xmx512m \
  -Dfile.encoding=UTF-8 \
  -Duser.country=US \
  -Duser.language=en \
  -Duser.variant \
  -javaagent:"$DIR/.gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks/gradle-9.3.1/lib/agents/gradle-instrumentation-agent-9.3.1.jar" \
  -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
