package org.netcrusher.core.filter;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
     * Add a new factories to the list
     * @param factories Filter factory instances
     */
    public ByteBufferFilterFactories append(Collection<ByteBufferFilterFactory> factories) {
        list.addAll(factories);
        return this;
    }

    /**
     * Add a new factories to the list
     * @param factories Filter factory instances
     */
    public ByteBufferFilterFactories append(ByteBufferFilterFactory... factories) {
        Collections.addAll(list, factories);
        return this;
    }

    /**
     * Creates a filter list for the specified local address
     * @param clientAddress Local client address
     * @return The list of filters
     */
    public ByteBufferFilter[] createFilters(InetSocketAddress clientAddress) {
        List<ByteBufferFilter> filters = new ArrayList<>(list.size());

        list.stream()
            .map(factory -> factory.create(clientAddress))
            .forEach(filters::add);

        return filters.stream().toArray(ByteBufferFilter[]::new);
    }

}
