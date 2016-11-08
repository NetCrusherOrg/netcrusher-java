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

# Maven

```xml
<dependency>
    <groupId>com.github.netcrusherorg</groupId>
    <artifactId>netcrusher-core</artifactId>
    <version>0.9</version>
</dependency>
```

# Performance

See [wiki page](https://github.com/NetCrusherOrg/netcrusher-java/wiki/Performance)

# License

Apache License Version 2.0, http://www.apache.org/licenses/LICENSE-2.0.html

# Links to the similar projects

* [Jepsen](http://jepsen.io) - Distributed Systems Safety Analysis
* [Java-NIO-TCP-Proxy](https://github.com/terma/java-nio-tcp-proxy/wiki) - Simple TCP proxy
* [netem](https://wiki.linuxfoundation.org/networking/netem) - Linux kernel module allows to distort network facilities
* [socat](https://linux.die.net/man/1/socat) - bridges everything with everything

