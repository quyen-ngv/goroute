package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicExpenseResponse {
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private String category;
    private String description;
    private Integer splitCount;
    private List<String> photoUrls;
    private LocalDateTime createdAt;
}
