package com.ds.goroute.service;

import com.ds.goroute.dto.request.PlaceTranslationRequest;
import com.ds.goroute.dto.response.PlaceTranslationResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.mapper.PlaceTranslationMapper;
import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.type.TranslationSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceTranslationService {

    private final PlaceTranslationMapper placeTranslationMapper;

    @Transactional
    public void syncTranslations(Place place,
                                 Map<String, PlaceTranslationRequest> translations) {
        if (place == null || place.getId() == null) {
            return;
        }

        Map<TranslationLocale, PlaceTranslationRequest> manual = normalizeTranslations(translations);
        TranslationText source = resolveSource(place, manual);

        upsert(place.getId(), TranslationLocale.DEFAULT, source.name(), source.description(),
                manual.containsKey(TranslationLocale.DEFAULT) ? TranslationSource.MANUAL : TranslationSource.LEGACY);

        for (Map.Entry<TranslationLocale, PlaceTranslationRequest> entry : manual.entrySet()) {
            TranslationLocale locale = entry.getKey();
            if (locale == TranslationLocale.DEFAULT) {
                continue;
            }
            PlaceTranslationRequest value = entry.getValue();
            upsert(place.getId(), locale, firstNonBlank(value.getName(), source.name()),
                    firstNonBlank(descriptionOf(value), source.description()), TranslationSource.MANUAL);
        }
    }

    @Transactional(readOnly = true)
    public PlaceTranslation resolve(UUID placeId, String requestedLocale) {
        TranslationLocale locale = TranslationLocale.fromCode(requestedLocale).orElse(TranslationLocale.DEFAULT);
        PlaceTranslation translation = placeTranslationMapper.findByPlaceIdAndLocale(placeId, locale);
        if (translation != null) {
            return translation;
        }
        if (locale != TranslationLocale.FALLBACK) {
            translation = placeTranslationMapper.findByPlaceIdAndLocale(placeId, TranslationLocale.FALLBACK);
            if (translation != null) {
                return translation;
            }
        }
        return placeTranslationMapper.findByPlaceIdAndLocale(placeId, TranslationLocale.DEFAULT);
    }

    @Transactional(readOnly = true)
    public Map<String, PlaceTranslationResponse> allResponses(UUID placeId) {
        Map<String, PlaceTranslationResponse> result = new LinkedHashMap<>();
        for (PlaceTranslation translation : placeTranslationMapper.findByPlaceId(placeId)) {
            result.put(translation.getLocale().code(), toResponse(translation));
        }
        return result;
    }

    private void upsert(UUID placeId, TranslationLocale locale, String name, String description, TranslationSource source) {
        if (name == null || name.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        placeTranslationMapper.upsert(PlaceTranslation.builder()
                .placeId(placeId)
                .locale(locale)
                .name(name.trim())
                .description(description != null && !description.isBlank() ? description.trim() : null)
                .translationSource(source)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private Map<TranslationLocale, PlaceTranslationRequest> normalizeTranslations(
            Map<String, PlaceTranslationRequest> translations) {
        Map<TranslationLocale, PlaceTranslationRequest> result = new LinkedHashMap<>();
        if (translations == null || translations.isEmpty()) {
            return result;
        }
        translations.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            TranslationLocale.fromCode(key).ifPresent(locale -> {
                if (hasText(value.getName()) || hasText(descriptionOf(value))) {
                    result.put(locale, value);
                }
            });
        });
        return result;
    }

    private TranslationText resolveSource(Place place, Map<TranslationLocale, PlaceTranslationRequest> translations) {
        PlaceTranslationRequest source = translations.get(TranslationLocale.DEFAULT);
        if (source != null) {
            return new TranslationText(firstNonBlank(source.getName(), place.getTitle()),
                    firstNonBlank(descriptionOf(source), place.getDescriptions()));
        }
        return new TranslationText(place.getTitle(), place.getDescriptions());
    }

    private PlaceTranslationResponse toResponse(PlaceTranslation translation) {
        return PlaceTranslationResponse.builder()
                .locale(translation.getLocale().code())
                .name(translation.getName())
                .description(translation.getDescription())
                .translationSource(translation.getTranslationSource())
                .build();
    }

    private String descriptionOf(PlaceTranslationRequest request) {
        return request == null ? null : firstNonBlank(request.getDescription(), request.getDescriptions());
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TranslationText(String name, String description) {
        private TranslationText {
            name = Objects.requireNonNullElse(name, "");
        }
    }
}
