SHELL:=/bin/bash
BASEPATH=$(CURDIR)
BUILDPATH=$(BASEPATH)/build
MAKEPATH=$(BASEPATH)/make

# gradle parameters
GRADLECMD=$(shell which gradle)

# docker parameters
DOCKERCMD=$(shell which docker)
DOCKERBUILD=$(DOCKERCMD) build
DOCKERRMIMAGE=$(DOCKERCMD) rmi
DOCKERPULL=$(DOCKERCMD) pull
DOCKERIMAGES=$(DOCKERCMD) images
DOCKERSAVE=$(DOCKERCMD) save
DOCKERCOMPOSECMD=$(shell which docker-compose)
DOCKERTAG=$(DOCKERCMD) tag
DOCKERRUN=$(DOCKERCMD) run

REGISTRYSERVER=
DOCKER_IMAGE_NAME=tmaxcloudck/hypercloud4-secret-watcher
VERSIONTAG=b4.1.0.10a

# pull/push image
PUSHSCRIPTPATH=$(MAKEPATH)
PUSHSCRIPTNAME=pushimage.sh
REGISTRYUSER=
REGISTRYPASSWORD=

.PHONY: build
build:
	@echo "build docker image"
	@$(GRADLECMD) build
	mkdir -p $(MAKEPATH)/bin
	cp $(BUILDPATH)/libs/* $(MAKEPATH)/bin
	@$(DOCKERBUILD) -t $(DOCKER_IMAGE_NAME):$(VERSIONTAG) ./make

.PHONY: push-image
push-image:
	@echo "pusing image"
	@$(DOCKERTAG) $(DOCKER_IMAGE_NAME):$(VERSIONTAG) $(REGISTRYSERVER)$(DOCKER_IMAGE_NAME):$(VERSIONTAG)
	@$(PUSHSCRIPTPATH)/$(PUSHSCRIPTNAME) $(REGISTRYSERVER)$(DOCKER_IMAGE_NAME):$(VERSIONTAG) \
		$(REGISTRYUSER) $(REGISTRYPASSWORD) $(REGISTRYSERVER)
	@$(DOCKERRMIMAGE) $(REGISTRYSERVER)$(DOCKER_IMAGE_NAME):$(VERSIONTAG)

.PHONY: run
run:
	@$(DOCKERRUN) --name secretwatcher -it --rm  \
		-v /etc/docker/certs.d:/etc/docker/certs.d \
		-v "${HOME}/.kube/":/kube \
		-e KUBECONFIG=/kube/config \
		-e LOGLEVEL=TRACE \
		$(DOCKER_IMAGE_NAME):$(VERSIONTAG)

.PHONY: smoke
smoke: build run

