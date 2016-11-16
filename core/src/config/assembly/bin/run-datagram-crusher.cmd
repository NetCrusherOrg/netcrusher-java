@ECHO OFF

set JAVA_OPTS=-XX:+UseParNewGC -XX:+UseConcMarkSweepGC -Dlogback.configurationFile=logback.xml %JAVA_OPTS%

set CRUSHER_BIN=%~dp0
set CRUSHER_LIB=%CRUSHER_BIN%\..\lib

set CLASSPATH=%CRUSHER_LIB%\*

java %JAVA_OPTS% -classpath %CLASSPATH% org.netcrusher.datagram.main.DatagramCrusherMain %1 %2