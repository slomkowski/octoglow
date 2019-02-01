#!/usr/bin/env bash

mvn -DskipTests package

OCTOGLOW_HOST='octoglow'
JAR_FILE='target/octoglowd-1.0-SNAPSHOT-jar-with-dependencies.jar'

scp ${JAR_FILE} ${OCTOGLOW_HOST}:/home/octoglow/octoglowd/
ssh ${OCTOGLOW_HOST} 'supervisorctl restart octoglowd'
