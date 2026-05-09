package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Place {
    private UUID id;
    
    // Google Maps IDs
    private String placeId;
    private String cid;
    private String dataId;
    private UUID inputId;
    
    // Basic Info
    private String title;
    private String category;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String plusCode;
    private String timezone;
    
    // Contact & Web
    private String phone;
    private String website;
    private String googleMapsLink;
    
    // Rating & Reviews
    private Integer reviewCount;
    private BigDecimal reviewRating;
    private String reviewsPerRating; // JSON as String
    
    // Media
    private String thumbnail;
    private String images; // JSON as String
    
    // Details
    private String descriptions;
    private String status;
    private String priceRange;
    
    // Hours & Booking
    private String openHours; // JSON as String
    private String popularTimes; // JSON as String
    private String reservations; // JSON as String
    private String orderOnline; // JSON as String
    private String menu; // JSON as String
    
    // Additional Info
    private String completeAddress; // JSON as String
    private String about; // JSON as String
    private String owner; // JSON as String
    private String emails; // JSON as String
    
    // Raw data backup
    private String rawData; // JSON as String
    
    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Transient field for distance calculation (not stored in DB)
    private Double distance;
}
