package org.netcrusher.datagram.callback;

import java.net.InetSocketAddress;

@FunctionalInterface
public interface DatagramClientCreation {

    void created(InetSocketAddress clientAddress);

}
