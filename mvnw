#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Shell Script
# ----------------------------------------------------------------------------

MAVEN_PROJECTBASEDIR="$(cd "$([ ! -z "$0" ] && echo "$(dirname "$0")" || echo ".")" && pwd -P)"
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER="org.apache.maven.wrapper.MavenWrapperMain"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Maven Wrapper JAR..."
    curl -fsSL -o "$WRAPPER_JAR" "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
fi

exec java -Dfile.encoding=UTF-8 -jar "$WRAPPER_JAR" "$@"
