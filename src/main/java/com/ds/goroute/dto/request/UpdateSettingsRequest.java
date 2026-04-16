package com.ds.goroute.dto.request;

import com.ds.goroute.type.LocationTracking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsRequest {
    private String defaultCurrency;
    private String defaultTravelMode;
    private LocationTracking locationTracking;
    private String language;
    private String theme;
}
