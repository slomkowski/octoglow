#!/usr/bin/env bash

set -e
set -u

JAVA_HOME=/usr/lib/jvm/java-11-openjdk/ mvn -DskipTests package

OCTOGLOW_HOST='octoglow'
JAR_FILE='target/octoglowd-1.0-SNAPSHOT-jar-with-dependencies-with-exclude-files.jar'

scp ${JAR_FILE} ${OCTOGLOW_HOST}:/home/octoglow/octoglowd/octoglowd.jar
ssh ${OCTOGLOW_HOST} 'supervisorctl restart octoglowd'
