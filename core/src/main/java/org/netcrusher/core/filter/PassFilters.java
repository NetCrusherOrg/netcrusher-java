package org.netcrusher.core.filter;

public final class PassFilters {

    private PassFilters() {
    }

    /**
     * All filters should return true
     * @param filters Filters
     * @return Combined filter
     */
    public static PassFilter all(final PassFilter... filters) {
        if (filters == null || filters.length == 0) {
            throw new IllegalArgumentException("Filter array is empty");
        }

        return (clientAddress, bb) -> {
            for (PassFilter filter : filters) {
                if (!filter.check(clientAddress, bb)) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * At least one filter should return true
     * @param filters Filters
     * @return Combined filter
     */
    public static PassFilter one(final PassFilter... filters) {
        if (filters == null || filters.length == 0) {
            throw new IllegalArgumentException("Filter array is empty");
        }

        return (clientAddress, bb) -> {
            for (PassFilter filter : filters) {
                if (filter.check(clientAddress, bb)) {
                    return true;
                }
            }
            return false;
        };
    }
}
