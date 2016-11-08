# NetCrusher for Java

NetCrusher is TCP/UDP proxy for Java that is placed in the middle between client and server and allows to check both sides on failover.

* emulates network failures
* emulates frozen connection
* supports both TCP and UDP
* supports multiple dynamic connections through the same proxy tunnel
* allows to check the state of connections
* allows to filter/dump data
* supports throttling (delay and/or throughput control)
* garbage-less buffering
* high perfomance (no excessive copying for TCP)
* supports IP4/IP6

NetCrusher is build on top of Java 8 NIO and has no external dependencies except [SLF4J](http://www.slf4j.org/).

Documentation is available on [wiki](https://github.com/NetCrusherOrg/netcrusher-java/wiki)

# TCP

```java
NioReactor reactor = new NioReactor();

TcpCrusher crusher = TcpCrusherBuilder.builder()
    .withReactor(reactor)
    .withBindAddress("localhost", 10080)
    .withConnectAddress("google.com", 80)
    .buildAndOpen();

// ... some actions

// emulate reconnect
crusher.reopen();

// ... check the client connection is reestablished successfully

// closing
crusher.close();
reactor.close();
```

# UDP

```java
NioReactor reactor = new NioReactor();

DatagramCrusher crusher = DatagramCrusherBuilder.builder()
    .withReactor(reactor)
    .withBindAddress("localhost", 10188)
    .withConnectAddress("time-nw.nist.gov", 37)
    .buildAndOpen();

// ... some actions

// check data is sent
Assert.assertTrue(crusher.getInner().getReadDatagramMeter().getTotal() > 0);

// closing
crusher.close();
reactor.close();
```

# Additional samples

Checks additional samples in the project root folder:

* [sample-hsqldb-bonecp](samples/sample-hsqldb-bonecp/src/test/java/org/netcrusher)
* [sample-datagram-rfc868](samples/sample-datagram-rfc868/src/test/java/org/netcrusher)
* [sample-zookeper](samples/sample-zookeeper/src/test/java/org/netcrusher)
* [sample-apache-http](samples/sample-apache-http/src/test/java/org/netcrusher)

# Command line 

For manual QA the command-line wrapper is available both for TCP and Datagram mode

```
$ ./run-tcp-crusher.sh 127.0.0.1:12345 google.com:80
# Version: 0.8
# Print `HELP` for the list of the commands
# enter the command in the next line
CLOSE
[20:19:20.586] INFO  TcpCrusher </127.0.0.1:12345>-<google.com/64.233.161.101:80> is closed
[20:19:20.586] INFO  Crusher is closed
# enter the command in the next line
OPEN
[20:19:21.655] INFO  TcpCrusher </127.0.0.1:12345>-<google.com/64.233.161.101:80> is open
[20:19:21.655] INFO  Crusher is open
```

# TCP performance

## iperf3 without a proxy (loopback interface)

```
onnecting to host 127.0.0.1, port 50100
[  4] local 127.0.0.1 port 32960 connected to 127.0.0.1 port 50100
[ ID] Interval           Transfer     Bandwidth       Retr  Cwnd
[  4]   0.00-1.00   sec  4.17 GBytes  35843 Mbits/sec    0   1.69 MBytes       
[  4]   1.00-2.00   sec  3.80 GBytes  32660 Mbits/sec    0   1.69 MBytes       
[  4]   2.00-3.00   sec  4.88 GBytes  41934 Mbits/sec    0   1.69 MBytes       
[  4]   3.00-4.00   sec  5.00 GBytes  42985 Mbits/sec    0   1.69 MBytes       
[  4]   4.00-5.00   sec  4.52 GBytes  38808 Mbits/sec    0   1.69 MBytes       
[  4]   5.00-6.00   sec  4.10 GBytes  35203 Mbits/sec    0   1.69 MBytes       
[  4]   6.00-7.00   sec  4.89 GBytes  42027 Mbits/sec    0   1.81 MBytes       
[  4]   7.00-8.00   sec  4.81 GBytes  41336 Mbits/sec    0   1.94 MBytes       
[  4]   8.00-9.00   sec  4.58 GBytes  39336 Mbits/sec    0   1.94 MBytes       
[  4]   9.00-10.00  sec  4.56 GBytes  39145 Mbits/sec    0   1.94 MBytes       
- - - - - - - - - - - - - - - - - - - - - - - - -
[ ID] Interval           Transfer     Bandwidth       Retr
[  4]   0.00-10.00  sec  45.3 GBytes  38928 Mbits/sec    0             sender
[  4]   0.00-10.00  sec  45.3 GBytes  38921 Mbits/sec                  receiver
```

## iperf3 with TcpCrusher (loopback interface)

```
Connecting to host 127.0.0.1, port 50101
[  4] local 127.0.0.1 port 33826 connected to 127.0.0.1 port 50101
[ ID] Interval           Transfer     Bandwidth       Retr  Cwnd
[  4]   0.00-1.00   sec  1.38 GBytes  11886 Mbits/sec    0   3.00 MBytes       
[  4]   1.00-2.00   sec  1.77 GBytes  15203 Mbits/sec    0   3.12 MBytes       
[  4]   2.00-3.00   sec  1.96 GBytes  16801 Mbits/sec    0   3.12 MBytes       
[  4]   3.00-4.00   sec  2.03 GBytes  17480 Mbits/sec    0   3.12 MBytes       
[  4]   4.00-5.00   sec  1.75 GBytes  15004 Mbits/sec    0   3.12 MBytes       
[  4]   5.00-6.00   sec  2.09 GBytes  17995 Mbits/sec    0   3.12 MBytes       
[  4]   6.00-7.00   sec  2.02 GBytes  17332 Mbits/sec    0   3.12 MBytes       
[  4]   7.00-8.00   sec  2.13 GBytes  18297 Mbits/sec    0   3.12 MBytes       
[  4]   8.00-9.00   sec  2.14 GBytes  18363 Mbits/sec    0   3.12 MBytes       
[  4]   9.00-10.00  sec  2.07 GBytes  17763 Mbits/sec    0   3.25 MBytes       
- - - - - - - - - - - - - - - - - - - - - - - - -
[ ID] Interval           Transfer     Bandwidth       Retr
[  4]   0.00-10.00  sec  19.3 GBytes  16612 Mbits/sec    0             sender
[  4]   0.00-10.00  sec  19.3 GBytes  16612 Mbits/sec                  receiver
```

# UDP performance

## iperf3 without a proxy (loopback interface)

```
Connecting to host 127.0.0.1, port 50100
[  4] local 127.0.0.1 port 56010 connected to 127.0.0.1 port 50100
[ ID] Interval           Transfer     Bandwidth       Total Datagrams
[  4]   0.00-1.00   sec   112 KBytes  0.92 Mbits/sec  14  
[  4]   1.00-2.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   2.00-3.00   sec   128 KBytes  1.05 Mbits/sec  16  
[  4]   3.00-4.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   4.00-5.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   5.00-6.00   sec   128 KBytes  1.05 Mbits/sec  16  
[  4]   6.00-7.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   7.00-8.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   8.00-9.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   9.00-10.00  sec   128 KBytes  1.05 Mbits/sec  16  
- - - - - - - - - - - - - - - - - - - - - - - - -
[ ID] Interval           Transfer     Bandwidth       Jitter    Lost/Total Datagrams
[  4]   0.00-10.00  sec  1.19 MBytes  1.00 Mbits/sec  0.044 ms  0/152 (0%)  
[  4] Sent 152 datagrams
```

## iperf3 with DatagramCrusher (loopback interface)

```
Connecting to host 127.0.0.1, port 50101
[  4] local 127.0.0.1 port 46245 connected to 127.0.0.1 port 50101
[ ID] Interval           Transfer     Bandwidth       Total Datagrams
[  4]   0.00-1.00   sec   112 KBytes  0.92 Mbits/sec  14  
[  4]   1.00-2.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   2.00-3.00   sec   128 KBytes  1.05 Mbits/sec  16  
[  4]   3.00-4.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   4.00-5.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   5.00-6.00   sec   128 KBytes  1.05 Mbits/sec  16  
[  4]   6.00-7.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   7.00-8.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   8.00-9.00   sec   120 KBytes  0.98 Mbits/sec  15  
[  4]   9.00-10.00  sec   128 KBytes  1.05 Mbits/sec  16  
- - - - - - - - - - - - - - - - - - - - - - - - -
[ ID] Interval           Transfer     Bandwidth       Jitter    Lost/Total Datagrams
[  4]   0.00-10.00  sec  1.19 MBytes  1.00 Mbits/sec  0.057 ms  0/152 (0%)  
[  4] Sent 152 datagrams
```

# Maven

```xml
<dependency>
    <groupId>com.github.netcrusherorg</groupId>
    <artifactId>netcrusher-core</artifactId>
    <version>0.8</version>
</dependency>
```

# License

Apache License Version 2.0, http://www.apache.org/licenses/LICENSE-2.0.html

# Links to the similar projects

* [Jepsen](http://jepsen.io) - Distributed Systems Safety Analysis
* [Java-NIO-TCP-Proxy](https://github.com/terma/java-nio-tcp-proxy/wiki) - Simple TCP proxy
* [netem](https://wiki.linuxfoundation.org/networking/netem) - Linux kernel module allows to distort network facilities
* [socat](https://linux.die.net/man/1/socat) - bridges everything with everything

