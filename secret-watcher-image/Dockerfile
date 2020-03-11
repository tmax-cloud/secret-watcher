FROM alpine:3

LABEL maintainer="seonho_choi@tmax.co.kr"

ADD ./secretwatcher /home/tmax/secretwatcher
ADD ./start.sh /home/tmax/
RUN apk update ; \
	apk upgrade ; \
	apk add openjdk8 ; \
	chmod 775 -R /home/tmax/secretwatcher ; \
	chmod 775 /home/tmax/start.sh ; \
	sed -i 's/\r$//' /home/tmax/start.sh

CMD ["/bin/sh", "-c", "/home/tmax/start.sh"]
