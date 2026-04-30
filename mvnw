#!/bin/sh

BASEDIR=$(cd "$(dirname "$0")"; pwd -P)
WRAPPER_DIR="$BASEDIR/.mvn/wrapper"
WRAPPER_PROPERTIES="$WRAPPER_DIR/maven-wrapper.properties"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  WRAPPER_URL=""
  if [ -f "$WRAPPER_PROPERTIES" ]; then
    WRAPPER_URL=$(grep -E '^wrapperUrl=' "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
  fi
  if [ -z "$WRAPPER_URL" ]; then
    WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
  fi

  mkdir -p "$WRAPPER_DIR"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
  else
    echo "Cannot download Maven Wrapper jar; please install curl or wget."
    exit 1
  fi
fi

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi

MAVEN_PROJECTBASEDIR="$BASEDIR"

exec "$JAVA_EXE" -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
  -cp "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
