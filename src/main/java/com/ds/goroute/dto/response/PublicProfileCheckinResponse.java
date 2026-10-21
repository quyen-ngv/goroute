package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One check-in on the public profile web page.
 *
 * <p>Deliberately narrower than {@link UserCheckinResponse}: the page is open to anyone with
 * the link and to search engines, so it carries what a reader sees on the card and nothing
 * else -- no phone coordinates, no reward points, no verification evidence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicProfileCheckinResponse {
    private UUID id;
    private UUID placeId;
    /** The catalogue name, else the name the author typed, else the map's name for the spot. */
    private String placeName;
    private String province;
    private String caption;
    private Integer overallRating;
    private List<String> photoUrls;
    private int likeCount;
    private int commentCount;
    /** The day of the visit; {@link #createdAt} is when it was posted. */
    private LocalDateTime visitedAt;
    private LocalDateTime createdAt;
}
