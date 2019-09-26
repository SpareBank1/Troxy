#!/bin/bash

source "./build-configuration.sh"

hasDockerCreds() {
    local -r docker_creds="${DOCKER_USERNAME}${DOCKER_PASSWORD}"

    if [ "$docker_creds" = '' ]; then
        return 1
    fi
}

dockerLogin() {
    echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
}

dockerPush() {
    docker push "$TROXY_DOCKER_IMAGE:$DOCKER_REVISION"
}

die() {
    echo "$0: " "$@" >&2
    exit 1
}

disable() {
    local -r reason="$1"
    local description=''

    case "$reason" in
        'dockerCreds')
            description="because of missing Docker credentials (use DOCKER_USERNAME and DOCKER_PASSWORD env. variables)"
            ;;
        *)
            description="for no reason"
            ;;
    esac
    printf "%s: Disabled %s\n" "$0" "$description" >&2
    exit 0
}

hasDockerCreds || disable 'dockerCreds'

dockerLogin || die 'Docker login failure'
dockerPush || die 'Docker push failure'
