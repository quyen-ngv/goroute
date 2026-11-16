package com.ds.goroute.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceTranslationRequest {
    private String name;
    private String description;
    private String descriptions;
    /** MANUAL (the default when blank) or GOOGLE; anything else is read as MANUAL. */
    private String source;
}
