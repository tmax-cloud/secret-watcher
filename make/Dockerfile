FROM openjdk:8-jre-alpine3.9

LABEL maintainer="taejune_ahn@tmax.co.kr"

ADD ../build/secretwatcher /app
ADD ./conf/* /app

RUN apk update ; \
	apk add tzdata ; \
	chmod 775 -R /app ; \

CMD ["/usr/bin/java", "-jar", "-Dlogback.configurationFile=/app/logback.xml","/app/lib/hypercloud4-secret-watcher.jar"]
