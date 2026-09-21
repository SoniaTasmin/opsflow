/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.web;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.function.Function;

/**
 * Deliberately our own type rather than Spring Data's {@code Page}: serialising {@code Page}
 * exposes framework internals as a public API contract.
 */
public record PageResponse<T>(
        @Schema(requiredMode = RequiredMode.REQUIRED) List<T> content,
        @Schema(requiredMode = RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = RequiredMode.REQUIRED) int totalPages) {

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

    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResponse<>(
                content.stream().map(mapper).map(r -> (R) r).toList(), page, size, totalElements, totalPages);
    }
}
