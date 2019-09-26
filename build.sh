#!/bin/bash

source "./build-configuration.sh"

goal() {
    local -r deploy_creds="${SONATYPE_USERNAME}${SONATYPE_PASSWORD}"

    if [ "$deploy_creds" = '' ]; then
        echo 'install'
    else
        echo 'deploy'
    fi
}

mvnDeploy() {
    ./mvnw "$(goal)" \
        -s ./settings.xml \
        -Drevision="$MAVEN_REVISION" \
        -Dsonatype.username="$SONATYPE_USERNAME" \
        -Dsonatype.password="$SONATYPE_PASSWORD"
    }

dockerBuild() {
    docker build -t "$TROXY_DOCKER_IMAGE:$DOCKER_REVISION" .
}

die() {
    echo "$0: " "$@" >&2
    exit 1
}

mvnDeploy || die 'Maven build failure'
dockerBuild || die 'Docker build failure'

./deploy.sh
