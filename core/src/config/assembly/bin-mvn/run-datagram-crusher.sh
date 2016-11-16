#!/bin/sh

if [ $# -ne 2 ]
then
    echo "run-datagram-crusher <bind-address:port> <connect-address:port>"
    exit 1
fi

export MAVEN_OPTS="-XX:+UseParNewGC -XX:+UseConcMarkSweepGC"

mvn \
    exec:java \
    -Dlogback.configurationFile=logback-highlight.xml \
    -Dexec.mainClass=org.netcrusher.datagram.main.DatagramCrusherMain \
    -Dexec.args="$1 $2"