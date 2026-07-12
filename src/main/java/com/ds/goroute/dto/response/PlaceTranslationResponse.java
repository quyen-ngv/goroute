package com.ds.goroute.dto.response;

import com.ds.goroute.type.TranslationSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceTranslationResponse {
    private String locale;
    private String name;
    private String description;
    private TranslationSource translationSource;
}
