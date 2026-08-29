package com.ds.goroute.entity;

import com.ds.goroute.type.ModeratedContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A comment on a social content item.  The parent points at another row in this table,
 * which keeps replies unbounded without creating a different table for every depth.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentComment {
    private UUID id;
    private ModeratedContentType contentType;
    private UUID contentId;
    private UUID parentId;
    private UUID userId;
    private String content;
    private Boolean isDeleted;
    private Integer replyCount;
    private Integer likeCount;
    private Boolean hasLiked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
