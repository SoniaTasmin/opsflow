/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.web;

import java.util.List;
import java.util.function.Function;

/**
 * The envelope every paginated collection endpoint returns.
 *
 * <p>Deliberately our own type rather than Spring Data's {@code Page}: serialising {@code Page}
 * directly exposes framework internals (and an unstable JSON shape) as a public API contract.
 * This record is the contract, and it is small enough to be obviously stable.
 *
 * @param content the items on this page
 * @param page zero-based page index
 * @param size requested page size
 * @param totalElements total matching items across all pages
 * @param totalPages total number of pages, derived from the above
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative");
        }
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    /** Maps the page's items while preserving its pagination metadata. */
    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResponse<>(
                content.stream().map(mapper).map(r -> (R) r).toList(), page, size, totalElements, totalPages);
    }
}
