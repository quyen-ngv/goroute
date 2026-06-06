package com.ds.goroute.dto.response;

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
public class ReviewScoreResponse {
    private UUID targetId;
    private String targetType;
    private BigDecimal score;
    private Integer reviewCount;
    private BigDecimal foodScore;
    private BigDecimal priceScore;
    private BigDecimal ambianceScore;
    private BigDecimal serviceScore;
    private String displayScore;
    private String displayLabel;
    private LocalDateTime lastCalculatedAt;
}
