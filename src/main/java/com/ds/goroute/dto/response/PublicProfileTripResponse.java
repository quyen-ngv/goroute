package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One public trip on the public profile web page. A card only: the trip itself opens on the
 * Ondetour page, which loads it through {@code GET /v1/api/public/trips/{tripId}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicProfileTripResponse {
    private UUID id;
    private String name;
    private String destination;
    private String coverImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime publicSharedAt;
}
