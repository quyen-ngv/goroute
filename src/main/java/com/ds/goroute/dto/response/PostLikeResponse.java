package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/** The like state after POST /posts/{postId}/like, shaped like the check-in one. */
@Value
@Builder
public class PostLikeResponse {
    UUID postId;
    int likeCount;
    boolean hasLiked;
}
