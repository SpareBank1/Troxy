#!/bin/sh

VERSION=${VERSION:-'local'}

mvnDeploy() {
    ./mvnw deploy -s ./settings.xml -Drevision="$VERSION"
}

dockerBuild() {
    docker build -t "$TROXY_DOCKER_IMAGE:$VERSION" .
}

dockerLogin() {
    echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
}

dockerPush() {
    docker push "$TROXY_DOCKER_IMAGE:$VERSION"
}

die() {
    echo "$0: " "$@" >&2
    exit 1
}

mvnDeploy || die 'Maven build failure'
dockerBuild || die 'Docker build failure'
dockerLogin || die 'Docker login failure'
dockerPush || die 'Docker push failure'

