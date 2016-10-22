package org.netcrusher.core.filter;

public final class TransformFilters {

    private TransformFilters() {
    }

    /**
     * Run all specified filter
     * @param filters Filters
     * @return Combined filter
     */
    public static TransformFilter all(final TransformFilter... filters) {
        if (filters == null || filters.length == 0) {
            throw new IllegalArgumentException("Filter array is empty");
        }

        return (clientAddress, bb) -> {
            for (TransformFilter filter : filters) {
                filter.transform(clientAddress, bb);
            }
        };
    }
}
