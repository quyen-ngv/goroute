package com.ds.goroute.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchBookPageSlotRequest {
    private Object value;
    private Boolean visible;
    private String frameStyle;
    private Map<String, Object> cropRect;
    private Map<String, Object> transform;
    private Map<String, Object> style;
    private Boolean locked;
}
