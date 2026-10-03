package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the admin queue of trip activities that point at no catalogue place. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUnlinkedActivityResponse {
    private UUID id;
    private UUID tripId;
    private String tripName;
    private UUID ownerId;
    private String ownerDisplayName;
    private String ownerEmail;
    private Integer dayNumber;
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private String category;
    private String placeId;
    private UUID customPlaceId;
    private LocalDateTime createdAt;
}
