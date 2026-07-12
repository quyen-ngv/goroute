package com.ds.goroute.entity;

import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.type.TranslationSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceTranslation {
    private UUID id;
    private UUID placeId;
    private TranslationLocale locale;
    private String name;
    private String description;
    private TranslationSource translationSource;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
