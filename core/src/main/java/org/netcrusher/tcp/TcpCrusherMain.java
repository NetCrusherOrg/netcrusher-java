package org.netcrusher.tcp;

import org.netcrusher.NetCrusher;
import org.netcrusher.core.AbstractCrusherMain;
import org.netcrusher.core.NioReactor;

import java.io.IOException;
import java.net.InetSocketAddress;

public class TcpCrusherMain extends AbstractCrusherMain {

    @Override
    protected NetCrusher create(NioReactor reactor,
                                InetSocketAddress bindAddress,
                                InetSocketAddress connectAddress) throws IOException
    {
        return TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(bindAddress)
            .withConnectAddress(connectAddress)
            .buildAndOpen();
    }

    @Override
    protected void status(NetCrusher crusher) throws IOException {
        System.out.printf("TCP crusher for <%s>-<%s>\n", crusher.getBindAddress(), crusher.getConnectAddress());
        super.status(crusher);

        if (crusher.isOpen()) {
            TcpCrusher tcpCrusher = (TcpCrusher) crusher;

            System.out.printf("Total accepted: %d\n", tcpCrusher.getCreatedPairsCount());

            for (TcpPair pair : tcpCrusher.getPairs()) {
                System.out.printf("Pair for <%s>\n", pair.getClientAddress());
                System.out.printf("\tinner total read bytes: %d\n", pair.getInnerTransfer().getTotalRead());
                System.out.printf("\tinner total sent bytes: %d\n", pair.getInnerTransfer().getTotalSent());
                System.out.printf("\touter total read bytes: %d\n", pair.getOuterTransfer().getTotalRead());
                System.out.printf("\touter total sent bytes: %d\n", pair.getOuterTransfer().getTotalSent());
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        TcpCrusherMain main = new TcpCrusherMain();
        main.run(arguments);
    }

}
