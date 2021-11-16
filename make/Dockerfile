FROM openjdk:8-jre-alpine3.9

LABEL maintainer="taejune_ahn@tmax.co.kr"

ADD bin/ /app
ADD conf/* /app

RUN apk update ; \
	apk add tzdata

WORKDIR /app

CMD ["/usr/bin/java", "-jar", "-Dlogback.configurationFile=logback.xml","hypercloud4-secret-watcher.jar"]
