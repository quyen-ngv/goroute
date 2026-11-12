package com.ds.goroute.dto.response;

import com.ds.goroute.type.PostMediaRole;
import com.ds.goroute.type.PostMediaType;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * {@code PostMedia} of the contract (.docs/BLOG_POSTS_PLAN_2026-11-10.md §4). The body refers to it
 * as {@code ![caption](media:<id>)}.
 */
@Value
@Builder
public class PostMediaResponse {
    UUID id;
    PostMediaType mediaType;
    PostMediaRole role;
    /** The image, or the video file. */
    String url;
    /** A video's poster; null for an image. */
    String thumbnailUrl;
    Integer durationSeconds;
    /** Always null today: media_assets has no width/height columns. Kept for the contract. */
    Integer width;
    Integer height;
    String caption;
}
