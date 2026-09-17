package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminPlaceResponse extends PlaceResponse {
    /** Curated tourist area this place resolves to; null until auto-map places it. */
    private UUID locationImageId;
    private String cid;
    private String dataId;
    private UUID inputId;
    private String plusCode;
    private Integer visitDurationMinutes;
    private String status;
    private String reservations;
    private String orderOnline;
    private String completeAddress;
    private String owner;
    private String emails;
    private String rawData;
    private String trustLevel;
    private Boolean jcurveDetected;
    private Boolean spikeDetected;
    private Integer authenticLowStarCount;
    private BigDecimal avgAuthenticityScore;
    private LocalDateTime scoreCalculatedAt;
}
