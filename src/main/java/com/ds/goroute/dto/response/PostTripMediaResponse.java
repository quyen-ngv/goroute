package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** {@code GET /posts/{postId}/trip-media}: everything the author may add to their trip story. */
@Value
@Builder
public class PostTripMediaResponse {
    List<PostTripMediaItemResponse> items;
}
