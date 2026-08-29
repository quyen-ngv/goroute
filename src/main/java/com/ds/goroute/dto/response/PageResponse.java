package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A bounded page of results.
 *
 * <p>Exists so list endpoints can return a typed payload instead of a raw map, and so the
 * total is carried explicitly rather than inferred by the caller from the page size.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        return PageResponse.<T>builder().items(items).total(total).page(page).size(size).build();
    }
}
