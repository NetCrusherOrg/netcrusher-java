package org.netcrusher.core.filter;

import java.util.ArrayList;
import java.util.List;

public final class PassFilters {

    private PassFilters() {
    }

    /**
     * All filters should return true
     * @param filterFactories Filter factories
     * @return Combined filter
     */
    public static PassFilterFactory all(final PassFilterFactory... filterFactories) {
        if (filterFactories == null || filterFactories.length == 0) {
            throw new IllegalArgumentException("Filter array is empty");
        }

        return (clientAddress) -> {
            final List<PassFilter> filters = new ArrayList<>(filterFactories.length);

            for (PassFilterFactory factory : filterFactories) {
                filters.add(factory.allocate(clientAddress));
            }

            return (bb) -> {
                for (PassFilter filter : filters) {
                    if (!filter.check(bb)) {
                        return false;
                    }
                }
                return true;
            };
        };
    }

    /**
     * At least one filter should return true
     * @param filterFactories Filter factories
     * @return Combined filter
     */
    public static PassFilterFactory one(final PassFilterFactory... filterFactories) {
        if (filterFactories == null || filterFactories.length == 0) {
            throw new IllegalArgumentException("Filter array is empty");
        }

        return (clientAddress) -> {
            final List<PassFilter> filters = new ArrayList<>(filterFactories.length);

            for (PassFilterFactory factory : filterFactories) {
                filters.add(factory.allocate(clientAddress));
            }

            return (bb) -> {
                for (PassFilter filter : filters) {
                    if (filter.check(bb)) {
                        return true;
                    }
                }
                return false;
            };
        };
    }
}
