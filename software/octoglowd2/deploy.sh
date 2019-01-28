#!/usr/bin/env bash

OCTOGLOW_HOST='octoglow'
JAR_FILE='target/octoglowd-1.0-SNAPSHOT-jar-with-dependencies.jar'

if [ ! -e ${JAR_FILE} ] ; then
    echo "No JAR file"
    exit 1
fi

scp ${JAR_FILE} ${OCTOGLOW_HOST}:/home/octoglow/octoglowd/
ssh ${OCTOGLOW_HOST} 'supervisorctl restart octoglowd'
