package com.ds.goroute.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLocationImageRequest {
    private String fullAddress;
    private String imageUrl;
    private String avatarUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer priority;
}
