package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * {@code PostAuthor} of the contract. {@code guide} is an active user_guide_grants row;
 * {@code guideTitle} null with {@code guide} true means the app's default badge label.
 */
@Value
@Builder
public class PostAuthorResponse {
    UUID userId;
    String displayName;
    String username;
    String avatarUrl;
    boolean guide;
    String guideTitle;
}
