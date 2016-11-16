#!/bin/sh

if [ $# -ne 2 ]
then
    echo "run-tcp-crusher <bind-address:port> <connect-address:port>"
    exit 1
fi

export MAVEN_OPTS="-XX:+UseParNewGC -XX:+UseConcMarkSweepGC"

mvn \
    exec:java \
    -Dlogback.configurationFile=logback-highlight.xml \
    -Dexec.mainClass=org.netcrusher.tcp.main.TcpCrusherMain \
    -Dexec.args="$1 $2"