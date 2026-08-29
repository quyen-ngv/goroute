package com.ds.goroute.dto.response;

import com.ds.goroute.type.ModeratedContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A flat response; clients build the reply tree from parentId. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentCommentResponse {
    private UUID id;
    private ModeratedContentType contentType;
    private UUID contentId;
    private UUID parentId;
    private UserResponse user;
    private String content;
    private boolean isDeleted;
    private int replyCount;
    private int likeCount;
    private boolean hasLiked;
    private LocalDateTime createdAt;
}
