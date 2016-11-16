#!/bin/sh

if [ $# -ne 2 ]
then
    echo "run-tcp-crusher <bind-address:port> <connect-address:port>"
    exit 1
fi

CRUSHER_BIN="$(dirname -- $(readlink -f -- $0))"
CRUSHER_LIB="$(dirname -- $CRUSHER_BIN)/lib"

JAVA_OPTS="-XX:+UseParNewGC -XX:+UseConcMarkSweepGC -Dlogback.configurationFile=$CRUSHER_BIN/logback.xml $JAVA_OPTS"

if [ -n "$JAVA_HOME" ]
then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="java"
fi

CLASSPATH="$CLASSPATH:$CRUSHER_LIB/*"

$JAVA $JAVA_OPTS -classpath $CLASSPATH org.netcrusher.tcp.main.TcpCrusherMain "$1" "$2"