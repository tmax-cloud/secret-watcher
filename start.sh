#!/bin/sh

nohup /usr/bin/java -jar /home/tmax/secretwatcher/hypercloud4-secret-watcher.jar >> /home/tmax/secretwatcher/stdout.log &

tail -f /dev/null