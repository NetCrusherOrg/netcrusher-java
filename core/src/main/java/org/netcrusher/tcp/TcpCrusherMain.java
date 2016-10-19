package org.netcrusher.tcp;

import org.netcrusher.core.AbstractCrusherMain;
import org.netcrusher.core.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

public class TcpCrusherMain extends AbstractCrusherMain<TcpCrusher> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpCrusherMain.class);

    @Override
    protected TcpCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException
    {
        return TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress)
            .withDeletionListener((pair) -> {
                LOGGER.info("Pair for <{}> is deleted", pair.getClientAddress());
                reportPair(pair);
            })
            .withCreationListener((pair -> {
                LOGGER.info("Pair for <{}> is created", pair.getClientAddress());
            }))
            .buildAndOpen();
    }

    @Override
    protected void status(TcpCrusher crusher) throws IOException {
        LOGGER.info("TCP crusher for <{}>-<{}>", crusher.getBindAddress(), crusher.getConnectAddress());
        super.status(crusher);

        if (crusher.isOpen()) {
            LOGGER.info("Total accepted: {}", crusher.getAcceptedCount());

            for (TcpPair pair : crusher.getPairs()) {
                LOGGER.info("Pair for <{}>", pair.getClientAddress());
                reportPair(pair);
            }
        }
    }

    private void reportPair(TcpPair pair) {
        TcpTransfer inner = pair.getInnerTransfer();
        LOGGER.info("\tinner total read bytes: {}", inner.getReadMeter().getTotal());
        LOGGER.info("\tinner total sent bytes: {}", inner.getSentMeter().getTotal());

        TcpTransfer outer = pair.getOuterTransfer();
        LOGGER.info("\touter total read bytes: {}", outer.getReadMeter().getTotal());
        LOGGER.info("\touter total sent bytes: {}", outer.getSentMeter().getTotal());
    }

    public static void main(String[] arguments) throws Exception {
        TcpCrusherMain main = new TcpCrusherMain();
        main.run(arguments);
    }

}
