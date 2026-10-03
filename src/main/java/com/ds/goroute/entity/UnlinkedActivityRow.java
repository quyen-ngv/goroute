package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** An activity that names a spot the catalogue does not have, with the trip it belongs to. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlinkedActivityRow {
    private UUID id;
    private UUID tripId;
    private String tripName;
    private UUID ownerId;
    private Integer dayNumber;
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private String category;
    /** Whatever the client sent: blank, or an external id that resolves to no catalogue row. */
    private String placeId;
    private UUID customPlaceId;
    private LocalDateTime createdAt;
}
