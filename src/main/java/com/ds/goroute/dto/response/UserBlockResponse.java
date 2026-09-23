package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry in "people you have blocked".
 *
 * <p>Carries the blocked person's name and avatar because the list is read by the person
 * who made the decision, often months later, and a page of identifiers is a page nobody
 * can undo a mistake from.
 */
@Data
@Builder
public class UserBlockResponse {
    private UUID userId;
    private String fullName;
    private String avatarUrl;
    private String reason;
    private LocalDateTime createdAt;
}
