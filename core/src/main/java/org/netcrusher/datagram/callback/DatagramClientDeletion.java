package org.netcrusher.datagram.callback;

import org.netcrusher.core.meter.RateMeters;

import java.net.InetSocketAddress;

@FunctionalInterface
public interface DatagramClientDeletion {

    void deleted(InetSocketAddress clientAddress, RateMeters byteMeters, RateMeters packetMeters);

}
