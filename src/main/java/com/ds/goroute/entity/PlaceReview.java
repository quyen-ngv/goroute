package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceReview {
    private UUID id;
    private UUID placeId;
    
    // Reviewer Info
    private String reviewerName;
    private String profilePicture;
    
    // Review Content
    private Integer rating;
    private String description;
    private LocalDate reviewDate;
    
    // Images as JSON string
    private String images;
    
    private LocalDateTime createdAt;
}
