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
public class Checkin {
    private UUID id;
    private UUID activityId;
    private UUID userId;
    private LocalDateTime checkedInAt;
    private Integer rating;
    private String notes;
    private BigDecimal lat;
    private BigDecimal lng;
    private Boolean autoCheckin;
}
