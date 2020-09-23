#!/bin/bash

REGISTRY=tmaxcloudck
IMAGE=hypercloud4-secret-watcher
VERSION=b4.1.0.10

docker build -t ${REGISTRY}/${IMAGE}:${VERSION} .
docker tag ${REGISTRY}/${IMAGE}:${VERSION} ${REGISTRY}/${IMAGE}:latest
docker push ${REGISTRY}/${IMAGE}:${VERSION}
docker push ${REGISTRY}/${IMAGE}:latest