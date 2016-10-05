package org.netcrusher;

import org.netcrusher.core.filter.ByteBufferFilterRepository;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;

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
     * Closes the crusher and it's sockets. After the crusher is closed it can be reopened again
     * @throws IOException Throwed on IO problems
     * @see NetCrusher#open()
     * @see NetCrusher#isOpen()
     */
    @Override
    void close() throws IOException;

    /**
     * Checks is the crusher open
     * @return Return <em>true</em> if the crusher is open
     * @see NetCrusher#open()
     * @see NetCrusher#close()
     */
    boolean isOpen();

    /**
     * Closes and then reopens the crusher again
     * @throws IOException Throwed on IO problems
     * @throws IllegalStateException Throwed if the crusher is not open
     * @see NetCrusher#open()
     * @see NetCrusher#close()
     */
    void crush() throws IOException;

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
     * Get filter repository to add a filter to
     * @return Filter repository
     */
    ByteBufferFilterRepository getFilters();

}
