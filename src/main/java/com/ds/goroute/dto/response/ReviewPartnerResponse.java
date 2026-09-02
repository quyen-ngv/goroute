package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** The partner's published reply shown under a marketplace review. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewPartnerResponse {
    private String text;
    private String responderName;
    private LocalDateTime respondedAt;
}
