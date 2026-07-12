package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedPlace {
    private UUID id;
    private UUID userId;
    private String placeId;
    private String itemType;
    private UUID customPlaceId;
    private String name;
    private String address;
    private Double lat;
    private Double lng;
    private String category;
    private Double rating;
    private String photoUrl;
    private String[] tags;
    private LocalDateTime createdAt;
}
