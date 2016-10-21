package org.netcrusher;

import org.netcrusher.core.meter.RateMeters;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;

public interface NetCrusher extends NetFreezer, Closeable {

    /**
     * Opens the crusher and it's sockets
     * @throws IOException Throwed on IO problems
     * @throws IllegalStateException Throwed if the crusher is already open
     * @see NetCrusher#close()
     * @see NetCrusher#isOpen()
     */
    void open() throws IOException;

    /**
     * Closes the crusher and it's sockets. After the crusher is closed it can be reopen again
     * @throws IOException Throwed on IO problems
     * @see NetCrusher#open()
     * @see NetCrusher#isOpen()
     */
    @Override
    void close() throws IOException;

    /**
     * Closes and then reopens the crusher again
     * @throws IOException Throwed on IO problems
     * @throws IllegalStateException Throwed if the crusher is not open
     * @see NetCrusher#open()
     * @see NetCrusher#close()
     */
    void reopen() throws IOException;

    /**
     * Checks is the crusher open
     * @return Return <em>true</em> if the crusher is open
     * @see NetCrusher#open()
     * @see NetCrusher#close()
     */
    boolean isOpen();

    /**
     * Get the address which is used to bind on
     * @return Local bind addresss
     */
    InetSocketAddress getBindAddress();

    /**
     * Get the adress which is used to connect to
     * @return Remote connect address
     */
    InetSocketAddress getConnectAddress();

    /**
     * Get addresses of clients which are connected to the crusher
     * @return Collection of addresses
     */
    Collection<InetSocketAddress> getClientAddresses();

    /**
     * Get byte statistics for the specified client address
     * @param clientAddress Client address
     * @return Statistic for the specified client address or null if such client is not found
     */
    RateMeters getClientByteMeters(InetSocketAddress clientAddress);

    /**
     * Close facilities for the specified client
     * @param clientAddress Client address
     * @return Return true if client is closed, return false if client is not found
     */
    boolean closeClient(InetSocketAddress clientAddress) throws IOException;

    /**
     * Get the total number of registered client since last crusher opening
     * @return Total number of clients
     */
    int getClientTotalCount();

}
