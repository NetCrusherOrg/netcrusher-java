package org.netcrusher.tcp.callback;

import java.net.InetSocketAddress;

@FunctionalInterface
public interface TcpClientCreation {

    void created(InetSocketAddress clientAddress);

}
