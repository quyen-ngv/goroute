package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A page without a total: {@code hasMore} says whether the next page has anything, found by
 * reading one row more than asked. Used where counting every match would cost more than the page
 * (posts, managed accounts; epic 20).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlicePageResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private boolean hasMore;

    /** Cuts the extra row off {@code fetched} (read with limit {@code size + 1}). */
    public static <T> SlicePageResponse<T> of(List<T> fetched, int page, int size) {
        boolean hasMore = fetched.size() > size;
        return SlicePageResponse.<T>builder()
                .items(hasMore ? List.copyOf(fetched.subList(0, size)) : fetched)
                .page(page)
                .size(size)
                .hasMore(hasMore)
                .build();
    }
}
