#!/usr/bin/env bash

set -e
set -u

rm -f build/libs/octoglowd-min.jar
JAVA_HOME=/usr/lib/jvm/java-17-openjdk/ ./gradlew proguard

OCTOGLOW_HOST='octoglow'
JAR_FILE='build/libs/octoglowd-min.jar'

ls -l --block-size=1K ${JAR_FILE}

scp ${JAR_FILE} ${OCTOGLOW_HOST}:/home/octoglow/octoglowd/octoglowd.jar
ssh ${OCTOGLOW_HOST} 'supervisorctl restart octoglowd'
