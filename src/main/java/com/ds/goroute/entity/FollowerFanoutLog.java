package com.ds.goroute.entity;

import com.ds.goroute.type.FollowerFanoutStatus;
import com.ds.goroute.type.FollowingPostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One fan-out of a post to its author's followers; a trace, never a work queue. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowerFanoutLog {
    private UUID id;
    private FollowingPostType postType;
    private UUID postId;
    private UUID authorId;
    private FollowerFanoutStatus status;
    private boolean pushEnabled;
    private int postCount;
    private int followerTotal;
    private int notifiedCount;
    private int pushedCount;
    private UUID lastFollowerId;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
