package org.netcrusher.datagram.callback;

import org.netcrusher.core.meter.RateMeters;

import java.net.InetSocketAddress;

@FunctionalInterface
public interface DatagramClientDeletion {

    void deleted(InetSocketAddress clientAdress, RateMeters byteMeters, RateMeters packetMeters);

}
