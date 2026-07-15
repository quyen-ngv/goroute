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
public class StarTransaction {
    private UUID id;
    private UUID userId;
    private Integer amount;
    private String transactionType;
    private String referenceKey;
    private String description;
    private LocalDateTime createdAt;
}
