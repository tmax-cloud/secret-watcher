FROM openjdk:8-jre-alpine3.9

LABEL maintainer="sukhee_yun@tmax.co.kr"

ADD ./secretwatcher /home/tmax/secretwatcher
ADD ./start.sh /home/tmax/
RUN apk update ; \
	apk add tzdata ; \
	chmod 775 -R /home/tmax/secretwatcher ; \
	chmod 775 /home/tmax/start.sh ; \
	sed -i 's/\r$//' /home/tmax/start.sh

CMD ["/bin/sh", "-c", "/home/tmax/start.sh"]
