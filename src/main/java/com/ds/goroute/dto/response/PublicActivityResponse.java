package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicActivityResponse {
    private UUID id;
    private Integer dayNumber;
    private UUID placeId; // Backend Place UUID
    private UUID bookingId;
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private LocalTime startTime;
    private LocalTime endTime;
    private String category;
    private BigDecimal rating;
    private Integer reviewCount;
    private String photoUrl;
    private List<String> memoryImageUrls;
    private List<MemoryImageResponse> memoryImageUrlsV2;
    private String description;
    private PlaceScoreResponse platformScore;
    private BigDecimal tripAvgScore;
    private List<UserReviewResponse> memberReviews;
    private List<PublicExpenseResponse> expenses;
    private List<PublicNoteResponse> notes;

    /**
     * Public check-ins written at this stop, oldest first.
     *
     * <p>A check-in is the written-up version of a stop — photos, a caption, a rating —
     * so the shared trip shows the post itself rather than re-deriving a photo strip
     * from the copies that landed in the trip gallery.
     */
    private List<UserCheckinResponse> checkins;
}
