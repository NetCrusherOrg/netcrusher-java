package org.netcrusher.filter;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ByteBufferFilterFactories {

    private final List<ByteBufferFilterFactory> list;

    public ByteBufferFilterFactories() {
        list = new CopyOnWriteArrayList<>();
    }

    /**
     * Clears the list
     */
    public void clear() {
        list.clear();
    }

    /**
     * Add a new factory to the list
     * @param factory Filter factory instance
     */
    public ByteBufferFilterFactories append(ByteBufferFilterFactory factory) {
        list.add(factory);
        return this;
    }

    /**
     * Creates a filter list for the specified local address
     * @param clientAddress Local client address
     * @return The list of filters
     */
    public List<ByteBufferFilter> createFilters(InetSocketAddress clientAddress) {
        List<ByteBufferFilter> filters = new ArrayList<>(list.size());

        for (ByteBufferFilterFactory factory : list) {
            filters.add(factory.create(clientAddress));
        }

        return filters;
    }
}
