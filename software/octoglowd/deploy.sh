#!/usr/bin/env bash

set -e
set -u

rm -f build/libs/octoglowd-min.jar
JAVA_HOME=/usr/lib/jvm/java-17-openjdk/ ./gradlew proguard

OCTOGLOW_HOST='octoglow'
JAR_FILE='build/libs/octoglowd-min.jar'

ls -l --block-size=1K ${JAR_FILE}

scp ${JAR_FILE} ${OCTOGLOW_HOST}:/home/octoglow/octoglowd/octoglowd.jar

# Staged, not installed: this file also defines the gpio eventlistener and resetbutton programs,
# so overwriting the live one is not something to do behind your back.
# See the header of deploy/supervisord-config.conf for the one-time reconciliation steps.
scp deploy/supervisord-config.conf ${OCTOGLOW_HOST}:/home/octoglow/octoglowd/supervisord-config.conf.new

ssh ${OCTOGLOW_HOST} 'supervisorctl restart octoglowd'
