package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A keyset page of saved posts. {@code totalCount} counts every save, available or not, so the
 * Saved tab can show it before the first page has been scrolled through.
 */
@Value
@Builder
public class SavedPostPageResponse {
    List<SavedPostResponse> items;
    String nextCursor;
    boolean hasMore;
    int totalCount;
}
