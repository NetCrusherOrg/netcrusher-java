package org.netcrusher.tcp.callback;

import org.netcrusher.core.meter.RateMeters;

import java.net.InetSocketAddress;

@FunctionalInterface
public interface TcpClientDeletion {

    void deleted(InetSocketAddress clientAdress, RateMeters byteMeters);

}
