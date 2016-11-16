@ECHO OFF

set CRUSHER_BIN=%~dp0
set MAVEN_OPTS=-XX:+UseParNewGC -XX:+UseConcMarkSweepGC %MAVEN_OPTS%

mvn exec:java -f %CRUSHER_BIN%\dependencies.xml -Dlogback.configurationFile=%CRUSHER_BIN%\logback.xml -Dexec.mainClass=org.netcrusher.datagram.main.DatagramCrusherMain -Dexec.args="%1 %2"