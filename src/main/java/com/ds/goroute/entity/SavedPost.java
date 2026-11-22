package com.ds.goroute.entity;

import com.ds.goroute.type.SavedPostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of saved_posts: a check-in or review a user saved to read later. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedPost {
    private UUID userId;
    private SavedPostType postType;
    private UUID postId;
    private LocalDateTime createdAt;
}
