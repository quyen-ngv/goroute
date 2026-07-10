package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripResponse {
    private UUID id;
    private String name;
    private String coverImageUrl;
    private List<String> memoryImageUrls;
    private String destination;
    private BigDecimal lat;
    private BigDecimal lng;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private BigDecimal budget;
    private String currency;
    private String visibility;
    private String shareCode;
    private Boolean shareExpenses;
    private Boolean shareNotes;
    private String description;
    private TripStatsResponse stats;
    private String userRole; // Role of current user in this trip (OWNER, EDITOR, VIEWER)
    private Integer viewCount;
    private Integer copyCount;
    private Integer helpfulVotes;
    private Integer unhelpfulVotes;
    private LocalDateTime publicSharedAt;
}
