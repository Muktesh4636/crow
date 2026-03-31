#!/usr/bin/env sh

# ----------------------------------------------------------------------------
#  Gradle startup script for POSIX
# ----------------------------------------------------------------------------

# ... (Standard Gradle Wrapper Script) ...
exec java -jar gradle/wrapper/gradle-wrapper.jar "$@"
