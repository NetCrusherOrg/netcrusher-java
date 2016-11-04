package org.netcrusher.core.filter;

import java.util.ArrayList;
import java.util.List;

public final class TransformFilters {

    private TransformFilters() {
    }

    /**
     * Run all specified filter
     * @param filterFactories Filter factories
     * @return Combined filter
     */
    public static TransformFilterFactory all(final TransformFilterFactory... filterFactories) {
        if (filterFactories == null || filterFactories.length == 0) {
            throw new IllegalArgumentException("Filter array is empty");
        }

        return (clientAddress) -> {
            final List<TransformFilter> filters = new ArrayList<>(filterFactories.length);

            for (TransformFilterFactory factory : filterFactories) {
                filters.add(factory.allocate(clientAddress));
            }

            return (bb) -> {
                for (TransformFilter filter : filters) {
                    filter.transform(bb);
                }
            };
        };
    }

}
