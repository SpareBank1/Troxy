#!/bin/sh

VERSION=${VERSION:-'local'}
TRAVIS_PULL_REQUEST=${TRAVIS_PULL_REQUEST:-''}

goal() {
    if [ "$1" = 'false' ]; then
        echo 'deploy'
    else
        echo 'install'
    fi
}

mvnDeploy() {
    ./mvnw "$(goal "$TRAVIS_PULL_REQUEST")" -s ./settings.xml -Drevision="$VERSION"
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

