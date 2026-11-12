package com.ds.goroute.entity;

import com.ds.goroute.type.PostMediaRole;
import com.ds.goroute.type.PostMediaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A post's photo or video: a {@code media_assets} row with entity_type 'POST' and entity_id the
 * post. A trip-story draft writes rows that point at the same URLs as the check-in or trip memory
 * they came from; the file is never copied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMedia {
    private UUID id;
    private UUID postId;
    private PostMediaType mediaType;
    private PostMediaRole role;
    /** The image, or the video file. */
    private String url;
    /** A video's poster frame; null for an image. */
    private String thumbnailUrl;
    private Integer durationSeconds;
    private String caption;
    private Integer position;
    private UUID uploadedBy;
    private LocalDateTime createdAt;

    public boolean isVideo() {
        return mediaType == PostMediaType.VIDEO;
    }
}
