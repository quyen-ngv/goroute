package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityStoryItemResponse {
    private UUID id;
    private UUID locationImageId;
    private String imageUrl;
    private String mediaType;
    private String videoUrl;
    private String thumbnailUrl;
    private String description;
    private UUID placeId;
    private CityStoryPlaceSummary place;
    private int likeCount;
    private boolean hasLiked;
    private boolean hasViewed;
    /**
     * The moment the story was posted, as an instant.
     *
     * <p>Deliberately not a {@code LocalDateTime}: that goes out over the wire with no
     * offset, so the client has to guess a zone for it. It guessed UTC while the server
     * was writing its own +07 wall clock, which put every story seven hours in the
     * future and left the feed reporting all of them as just posted. An instant carries
     * its own {@code Z} and cannot be read two ways.
     */
    private Instant createdAt;
}
