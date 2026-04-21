package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTripResponse {
    private UUID id;
    private String name;
    private String coverImageUrl;
    private String destination;
    private BigDecimal lat;
    private BigDecimal lng;
    private LocalDate startDate;
    private LocalDate endDate;
    private String currency;
    private List<PublicActivityResponse> activities;
    private List<PublicExpenseResponse> expenses;
    private List<PublicNoteResponse> notes;
}
