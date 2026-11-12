package com.ds.goroute.entity;

import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.PostStatus;
import com.ds.goroute.type.PostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of {@code posts} (V275): a blog post, epic 20. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    private UUID id;
    /** The author. For a managed account this is the account, never the admin who typed it. */
    private UUID userId;
    private PostType postType;
    private String title;
    private String excerpt;
    /** Markdown. Left out of list queries, which leave this null. */
    private String body;
    private UUID coverMediaId;
    private UUID tripId;
    private PostStatus status;
    private ContentVisibility visibility;
    /** The first publish. Kept by an unpublish, so a republish never announces the post again. */
    private LocalDateTime publishedAt;
    private Integer likeCount;
    private Integer readMinutes;
    private Boolean isRemoved;
    private LocalDateTime editedAt;
    /** Who acted: the author, or the admin writing for a managed account. */
    private UUID createdBy;
    private UUID updatedBy;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
    }

    /** What strangers may read: published, public and not removed. A takedown is checked apart. */
    public boolean isPubliclyReadable() {
        return isPublished() && visibility == ContentVisibility.PUBLIC && !Boolean.TRUE.equals(isRemoved);
    }
}
