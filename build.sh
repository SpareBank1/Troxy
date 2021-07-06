#!/bin/bash -ev

source "./build-configuration.sh"

dockerBuild() {
    docker build -t "$TROXY_DOCKER_IMAGE:$DOCKER_REVISION" .
}


./mvnw -B clean install
#dockerBuild

