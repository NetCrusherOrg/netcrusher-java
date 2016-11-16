@ECHO OFF

set MAVEN_OPTS=-XX:+UseParNewGC -XX:+UseConcMarkSweepGC

mvn exec:java -Dlogback.configurationFile=logback-plain.xml -Dexec.mainClass=org.netcrusher.datagram.main.DatagramCrusherMain -Dexec.args="%1 %2"