#!/bin/sh
#
# Gradle wrapper script for Unix
#

# Determine the Java command to use.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found."
fi

# Determine wrapper jar path
APP_HOME="$( cd "$( dirname "$0" )" && pwd )"
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" \
  "-Dorg.gradle.appname=gradlew" \
  -jar "$GRADLE_WRAPPER_JAR" \
  "$@"
