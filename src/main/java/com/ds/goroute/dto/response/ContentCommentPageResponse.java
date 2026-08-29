package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** A bounded cursor page for either root comments or one parent's direct replies. */
@Value
@Builder
public class ContentCommentPageResponse {
    List<ContentCommentResponse> comments;
    String nextCursor;
    boolean hasMore;
}
