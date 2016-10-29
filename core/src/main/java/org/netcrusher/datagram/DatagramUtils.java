package org.netcrusher.datagram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;

public final class DatagramUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramUtils.class);

    private static final String SOCKET_EXCEPTION_EPERM = "Operation not permitted";

    private DatagramUtils() {
    }

    public static void rethrowSocketException(SocketException e) throws SocketException {
        if (SOCKET_EXCEPTION_EPERM.equals(e.getMessage())) {
            // http://www.archivum.info/comp.protocols.tcp-ip/2009-05/00088/UDP-socket-amp-amp-sendto-amp-amp-EPERM.html
            // http://oosnmp.net/pipermail/snmp4j/2014-October/005433.html
            // https://github.com/netty/netty/issues/1258
            LOGGER.warn("EPERM socket error is registered");
        } else {
            throw e;
        }
    }

}
