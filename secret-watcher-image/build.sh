#!/bin/bash

REGISTRY=192.168.6.110:5000
IMAGE=hypercloud4-secret-watcher
VERSION=b4.1.0.5

docker build -t ${REGISTRY}/${IMAGE}:${VERSION} .
docker tag ${REGISTRY}/${IMAGE}:${VERSION} ${REGISTRY}/${IMAGE}:latest
docker push ${REGISTRY}/${IMAGE}:${VERSION}
docker push ${REGISTRY}/${IMAGE}:latest