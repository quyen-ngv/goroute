package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDestinationRequest {
    @Size(max = 255)
    @ModeratedText(contentType = ModeratedContentType.TRIP, visibility = ModerationVisibility.PUBLIC)
    private String name;
    @ModeratedText(contentType = ModeratedContentType.TRIP, visibility = ModerationVisibility.PUBLIC)
    private String address;
    private String placeId;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer orderIndex;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPrimary;
}
