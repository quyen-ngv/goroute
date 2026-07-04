package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookSlotResponse {
    private String slotId;
    private String type;
    private Object value;
    private Boolean visible;
    private String frameStyle;
    private Map<String, Object> cropRect;
    private Map<String, Object> transform;
    private Map<String, Object> style;
    private Boolean locked;
}
