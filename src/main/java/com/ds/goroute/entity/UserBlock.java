package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One person's decision that another may not write to them.
 *
 * <p>Not symmetric: the row belongs to {@code blockerId} and says nothing about what
 * {@code blockedId} wants. Two people who have blocked each other are two rows.
 *
 * <p>The blocked person is never told. A block that announces itself invites the argument
 * it was made to end, so the only thing they see is a message that does not arrive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBlock {

    private UUID blockerId;

    private UUID blockedId;

    /** Free text for the blocker and for moderators. Never shown to the blocked person. */
    private String reason;

    private LocalDateTime createdAt;
}
