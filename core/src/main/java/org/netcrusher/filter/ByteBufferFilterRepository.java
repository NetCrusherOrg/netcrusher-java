package org.netcrusher.filter;

public class ByteBufferFilterRepository {

    private final ByteBufferFilterFactories outgoing;

    private final ByteBufferFilterFactories incoming;

    public ByteBufferFilterRepository() {
        this.incoming = new ByteBufferFilterFactories();
        this.outgoing = new ByteBufferFilterFactories();
    }

    /**
     * List of outgoing filter factories
     * @return Repository
     */
    public ByteBufferFilterFactories getOutgoing() {
        return outgoing;
    }

    /**
     * List of incoming filter factories
     * @return Repository
     */
    public ByteBufferFilterFactories getIncoming() {
        return incoming;
    }
}

