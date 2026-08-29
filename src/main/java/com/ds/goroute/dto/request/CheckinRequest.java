package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinRequest {
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer rating;
    @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
    private String notes;
}
