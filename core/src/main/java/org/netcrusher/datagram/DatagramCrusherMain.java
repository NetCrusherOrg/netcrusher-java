package org.netcrusher.datagram;

import org.netcrusher.core.AbstractCrusherMain;
import org.netcrusher.core.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;

public class DatagramCrusherMain extends AbstractCrusherMain<DatagramCrusher> {

    private static final long MAX_IDLE_DURATION_MS = 60000;

    @Override
    protected DatagramCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException
    {
        return DatagramCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress)
            .withMaxIdleDurationMs(MAX_IDLE_DURATION_MS)
            .buildAndOpen();
    }

    @Override
    protected void status(DatagramCrusher crusher) throws IOException {
        System.out.printf("Datagram crusher for <%s>-<%s>\n", crusher.getBindAddress(), crusher.getConnectAddress());
        super.status(crusher);

        if (crusher.isOpen()) {
            System.out.printf("Inner\n");

            DatagramInner inner = crusher.getInner();
            System.out.printf("\ttotal read bytes: %s\n", inner.getReadByteMeter().getTotal());
            System.out.printf("\ttotal read datagrams: %s\n", inner.getReadDatagramMeter().getTotal());
            System.out.printf("\ttotal sent bytes: %s\n", inner.getSentByteMeter().getTotal());
            System.out.printf("\ttotal sent datagrams: %s\n", inner.getSentDatagramMeter().getTotal());

            for (DatagramOuter outer : crusher.getOuters()) {
                System.out.printf("Outer for <%s>\n", outer.getClientAddress());
                System.out.printf("\ttotal read bytes: %s\n", outer.getReadByteMeter().getTotal());
                System.out.printf("\ttotal read datagrams: %s\n", outer.getReadDatagramMeter().getTotal());
                System.out.printf("\ttotal sent bytes: %s\n", outer.getSentByteMeter());
                System.out.printf("\ttotal sent datagrams: %s\n", outer.getSentDatagramMeter().getTotal());
                System.out.printf("\tidle duration, ms: %d\n", outer.getIdleDurationMs());
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        DatagramCrusherMain main = new DatagramCrusherMain();
        main.run(arguments);
    }
}
