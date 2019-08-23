#!/bin/sh

VERSION=${VERSION:-'local'}

./mvnw deploy -s settings.xml -Drevision="$VERSION"

docker build -t "$TROXY_DOCKER_IMAGE:$VERSION" .
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
docker push "$TROXY_DOCKER_IMAGE:$VERSION"

