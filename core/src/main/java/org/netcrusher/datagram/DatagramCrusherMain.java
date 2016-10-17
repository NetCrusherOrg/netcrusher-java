package org.netcrusher.datagram;

import org.netcrusher.NetCrusher;
import org.netcrusher.core.AbstractCrusherMain;
import org.netcrusher.core.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;

public class DatagramCrusherMain extends AbstractCrusherMain {

    private static final long MAX_IDLE_DURATION_MS = 60000;

    @Override
    protected NetCrusher create(NioReactor reactor,
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
    protected void status(NetCrusher crusher) throws IOException {
        System.out.printf("Datagram crusher for <%s>-<%s>\n", crusher.getBindAddress(), crusher.getConnectAddress());
        super.status(crusher);

        if (crusher.isOpen()) {
            DatagramCrusher datagramCrusher = (DatagramCrusher) crusher;

            System.out.printf("Inner\n");

            System.out.printf("\ttotal read bytes: %d\n",
                datagramCrusher.getInner().getReadByteMeter().getTotalCount());
            System.out.printf("\ttotal read datagrams: %d\n",
                datagramCrusher.getInner().getReadDatagramMeter().getTotalCount());
            System.out.printf("\ttotal sent bytes: %d\n",
                datagramCrusher.getInner().getSentByteMeter().getTotalCount());
            System.out.printf("\ttotal sent datagrams: %d\n",
                datagramCrusher.getInner().getSentDatagramMeter().getTotalCount());

            for (DatagramOuter outer : datagramCrusher.getOuters()) {
                System.out.printf("Outer for <%s>\n", outer.getClientAddress());
                System.out.printf("\ttotal read bytes: %d\n",
                    outer.getReadByteMeter().getTotalCount());
                System.out.printf("\ttotal read datagrams: %d\n",
                    outer.getReadDatagramMeter().getTotalCount());
                System.out.printf("\ttotal sent bytes: %d\n",
                    outer.getSentByteMeter().getTotalCount());
                System.out.printf("\ttotal sent datagrams: %d\n",
                    outer.getSentDatagramMeter().getTotalCount());
                System.out.printf("\tidle duration, ms: %d\n",
                    outer.getIdleDurationMs());
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        DatagramCrusherMain main = new DatagramCrusherMain();
        main.run(arguments);
    }
}
