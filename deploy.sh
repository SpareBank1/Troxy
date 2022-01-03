#!/bin/bash -ev

source "./build-configuration.sh"

hasDockerCreds() {
    local -r docker_creds="$DOCKER_USERNAME$DOCKER_PASSWORD"

    if [ "$docker_creds" = '' ]; then
        return 1
    fi
}

dockerLogin() {
    echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
}

dockerPush() {
    docker tag "$TROXY_DOCKER_IMAGE:$DOCKER_REVISION" "$TROXY_DOCKER_IMAGE:latest"
    docker push "$TROXY_DOCKER_IMAGE:$DOCKER_REVISION"
    docker push "$TROXY_DOCKER_IMAGE:latest"
}

die() {
    echo "$0: " "$@" >&2
    exit 1
}

mvnDeploy() {
    mvn deploy \
        -Prelease \
        -s ./settings.xml \
        -Drevision="$MAVEN_REVISION" \
        -Dsonatype.username="$SONATYPE_USERNAME" \
        -Dsonatype.password="$SONATYPE_PASSWORD" \
        -Dgpg.passphrase="$OSSRH_GPG_SECRET_KEY_PASSWORD"
}

mvnDeploy || die 'Maven deploy failure'

hasDockerCreds || die 'Missing docker credentials'
dockerLogin || die 'Docker login failure'
dockerPush || die 'Docker push failure'
