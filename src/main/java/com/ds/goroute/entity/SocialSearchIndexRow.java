package com.ds.goroute.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One user, check-in or trip as the Feed search index reads it: only the text it matches on
 * and the signals it ranks by. Read by {@code UserMapper}, {@code UserCheckinMapper} and
 * {@code TripMapper} ({@code selectSearchIndex*}); the kind is implied by which one.
 */
@Data
public class SocialSearchIndexRow {
    private UUID id;
    /** The author; the user itself for a user row. */
    private UUID ownerId;
    /** Matched first: a person's name, a check-in's place, a trip's name and destinations. */
    private String title;
    /** A person's username; null for posts. */
    private String handle;
    /** Matched with less weight: bio, caption and area, trip description and stops. */
    private String body;
    /** Followers, likes, or votes and copies. */
    private Long popularity;
    /** When it was posted, for the freshness boost. */
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    /** Whether anyone but the author may find it now; a changed row that is not leaves the index. */
    private Boolean eligible;
}
