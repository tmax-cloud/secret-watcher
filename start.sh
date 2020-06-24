#!/bin/sh

export K8S_HOME=/home/tmax/secretwatcher
/usr/bin/java -jar -Dlogback.configurationFile=${K8S_HOME}/logback.xml ${K8S_HOME}/lib/hypercloud4-secret-watcher.jar