# NetCrusher for Java

NetCrusher is TCP/UDP proxy for java that sit in the middle between client and server and allows to check both sides on proper failover.

* emulates network failures that lead to remote socket closing
* emulates frozen connection
* allows to check the state of connections
* allows to filter/dump data
* throttling (delay and/or throughtput control)

NetCrusher is build on top of Java 8 NIO and has no external dependencies except [SLF4J](http://www.slf4j.org/).

Documentation is available on https://netcrusherorg.github.io/netcrusher-java/

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

// ... check the connection is reestablished

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

# Maven

```xml
<dependency>
    <groupId>com.github.netcrusherorg</groupId>
    <artifactId>netcrusher-core</artifactId>
    <version>0.7</version>
</dependency>
```

# License

Apache License Version 2.0, http://www.apache.org/licenses/LICENSE-2.0.html

# Links to similar projects

* [Jepsen](http://jepsen.io) - Distributed Systems Safety Analysis
* [Java-NIO-TCP-Proxy](https://github.com/terma/java-nio-tcp-proxy/wiki) - Simple TCP proxy
* [netem](https://wiki.linuxfoundation.org/networking/netem) - Linux kernel module for testing protocols
