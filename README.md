# NetCrusher

NetCrusher is TCP/UDP proxy that sit in the middle between client and server and allows to check both sides on proper failover functionality.

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
crusher.reopen();

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

// emulate disconnect
crusher.reopen();

// check everything is still allright

// closing
crusher.close();
reactor.close();
```
