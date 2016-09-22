# NetCrusher for Java

NetCrusher is TCP/UDP proxy for java that sit in the middle between client and server and allows to check both sides on proper failover.

* emulates network failures that lead to remote socket closing
* emulates slow network (TBD)
* emulates frozen connection (TBD)
* emulates broken data (TBD)

NetCrusher is build on top of Java NIO and has no external dependencies except [SLF4J](http://www.slf4j.org/).

# TCP

```java
NioReactor reactor = new NioReactor();

TcpCrusher crusher = TcpCrusherBuilder.builder()
    .withReactor(reactor)
    .withLocalAddress("localhost", 10080)
    .withRemoteAddress("google.com", 80)
    .build();

// now you connect to localhost:10080
SomeResource resource = new SomeResource("localhost", 10080);

// emulate disconnect
crusher.crush();

// check how the application goes
Assert.assertTrue(resource.isValid());

// closing
crusher.close();
reactor.close();
```

# UDP

```java
NioReactor reactor = new NioReactor();

DatagramCrusher crusher = DatagramCrusherBuilder.builder()
    .withReactor(reactor)
    .withLocalAddress("localhost", 10188)
    .withRemoteAddress("time-nw.nist.gov", 37)
    .build();

// start getting RFC-868 timestamp on localhost:10188

// emulate disconnect - listening socket on localhost:10188 will be reopened
crusher.crush();

// check everything is still allright

// closing
crusher.close();
reactor.close();
```

# Maven

```xml
<dependency>
    <groupId>com.github.netcrusherorg</groupId>
    <artifactId>netcrusher-core</artifactId>
    <version>0.4</version>
</dependency>
```

# License

Apache License Version 2.0, http://www.apache.org/licenses/LICENSE-2.0.html

# Other projects

* [Jepsen](http://jepsen.io) - Distributed Systems Safety Analysis
* [Java-NIO-TCP-Proxy](https://github.com/terma/java-nio-tcp-proxy/wiki) - Simple TCP proxy