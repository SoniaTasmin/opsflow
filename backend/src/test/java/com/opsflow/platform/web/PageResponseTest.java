/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void derivesTotalPagesByRoundingUp() {
        assertThat(PageResponse.of(List.of("a"), 0, 20, 41).totalPages()).isEqualTo(3);
    }

    @Test
    void anEmptyResultHasNoPages() {
        assertThat(PageResponse.of(List.of(), 0, 20, 0).totalPages()).isZero();
    }

    @Test
    void contentIsDefensivelyCopied() {
        List<String> source = new ArrayList<>(List.of("a"));
        PageResponse<String> page = PageResponse.of(source, 0, 20, 1);

        source.add("b");

        assertThat(page.content()).containsExactly("a");
    }

    @Test
    void mapPreservesPaginationMetadata() {
        PageResponse<Integer> mapped =
                PageResponse.of(List.of("a", "bb"), 2, 10, 42).map(String::length);

        assertThat(mapped.content()).containsExactly(1, 2);
        assertThat(mapped.page()).isEqualTo(2);
        assertThat(mapped.size()).isEqualTo(10);
        assertThat(mapped.totalElements()).isEqualTo(42);
        assertThat(mapped.totalPages()).isEqualTo(5);
    }

    @Test
    void rejectsNonsensicalPagination() {
        assertThatThrownBy(() -> PageResponse.of(List.of(), -1, 20, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageResponse.of(List.of(), 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
