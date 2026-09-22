package com.joblens.api.common.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope for paginated list endpoints.
 *
 * <p>Spring Data's own {@code Page} serialises to an unstable structure that
 * leaks internal fields, so every list endpoint returns this instead. Defining
 * it now means the first paginated endpoint does not invent its own shape.
 *
 * @param content       the items on this page, already mapped to DTOs
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements total number of matching items across all pages
 * @param totalPages    total number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /**
     * Maps a persistence-layer {@link Page} to a response, converting each
     * element with {@code mapper}. Entities must never be serialised directly,
     * so the mapper is required rather than optional.
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
