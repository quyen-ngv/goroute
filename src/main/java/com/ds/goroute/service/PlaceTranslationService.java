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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceTranslationService {

    private static final int ID_BATCH_SIZE = 500;

    private final PlaceTranslationMapper placeTranslationMapper;

    /**
     * Writes the place's names: the {@code vi} row (from the request, else {@code places.title}
     * as {@link TranslationSource#LEGACY}) and every other requested locale with the entry's
     * source. A row is replaced only by a source of the same or a higher rank, so a re-import
     * never overwrites a name an admin fixed.
     */
    @Transactional
    public void syncTranslations(Place place,
                                 Map<String, PlaceTranslationRequest> translations) {
        if (place == null || place.getId() == null) {
            return;
        }

        Map<TranslationLocale, PlaceTranslationRequest> requested = normalizeTranslations(translations);
        TranslationText source = resolveSource(place, requested);
        PlaceTranslationRequest vietnamese = requested.get(TranslationLocale.DEFAULT);

        upsert(place.getId(), TranslationLocale.DEFAULT, source.name(), source.description(),
                vietnamese != null ? requestSource(vietnamese) : TranslationSource.LEGACY);

        for (Map.Entry<TranslationLocale, PlaceTranslationRequest> entry : requested.entrySet()) {
            TranslationLocale locale = entry.getKey();
            if (locale == TranslationLocale.DEFAULT) {
                continue;
            }
            PlaceTranslationRequest value = entry.getValue();
            upsert(place.getId(), locale, firstNonBlank(value.getName(), source.name()),
                    firstNonBlank(descriptionOf(value), source.description()), requestSource(value));
        }
    }

    /**
     * Writes one name, keeping the stored description, under the rank rule of
     * {@link #syncTranslations}.
     *
     * @return false when a higher-ranked source already holds the row
     */
    @Transactional
    public boolean writeName(UUID placeId, TranslationLocale locale, String name, TranslationSource source) {
        LocalDateTime now = LocalDateTime.now();
        return placeTranslationMapper.upsertName(PlaceTranslation.builder()
                .placeId(placeId)
                .locale(locale)
                .name(name.trim())
                .translationSource(source)
                .createdAt(now)
                .updatedAt(now)
                .build()) > 0;
    }

    /**
     * Writes one description, keeping the stored name, under the rank rule of
     * {@link #syncTranslations}. A locale without a row yet gets one named {@code fallbackName}
     * (the name the place shows now), since a translation row always carries a name.
     *
     * @return false when a higher-ranked source already holds the row
     */
    @Transactional
    public boolean writeDescription(UUID placeId, TranslationLocale locale, String description,
                                    String fallbackName, TranslationSource source) {
        LocalDateTime now = LocalDateTime.now();
        String name = fallbackName == null || fallbackName.isBlank() ? "" : fallbackName.trim();
        return placeTranslationMapper.upsertDescription(PlaceTranslation.builder()
                .placeId(placeId)
                .locale(locale)
                .name(name)
                .description(description.trim())
                .translationSource(source)
                .createdAt(now)
                .updatedAt(now)
                .build()) > 0;
    }

    /** The requested locale, then the other Chinese script, then English, then Vietnamese. */
    @Transactional(readOnly = true)
    public PlaceTranslation resolve(UUID placeId, String requestedLocale) {
        return resolveFrom(placeTranslationMapper.findByPlaceId(placeId), requestedLocale);
    }

    @Transactional(readOnly = true)
    public Map<String, PlaceTranslationResponse> allResponses(UUID placeId) {
        Map<String, PlaceTranslationResponse> result = new LinkedHashMap<>();
        for (PlaceTranslation translation : placeTranslationMapper.findByPlaceId(placeId)) {
            result.put(translation.getLocale().code(), responseOf(translation));
        }
        return result;
    }

    /**
     * Every translation of the given places, grouped by place, in one query per 500 ids.
     * List endpoints resolve the locale from this in memory instead of querying per row.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<PlaceTranslation>> findByPlaceIds(Collection<UUID> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinctIds = placeIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, List<PlaceTranslation>> result = new HashMap<>();
        for (int from = 0; from < distinctIds.size(); from += ID_BATCH_SIZE) {
            List<UUID> batch = distinctIds.subList(from, Math.min(from + ID_BATCH_SIZE, distinctIds.size()));
            for (PlaceTranslation translation : placeTranslationMapper.findByPlaceIds(batch)) {
                result.computeIfAbsent(translation.getPlaceId(), ignored -> new ArrayList<>()).add(translation);
            }
        }
        return result;
    }

    /**
     * {@link #resolve} over an already loaded list: the requested locale, its
     * {@link TranslationLocale#sibling() sibling} (zh-CN ↔ zh-TW), {@link TranslationLocale#FALLBACK},
     * then {@link TranslationLocale#DEFAULT}; null when none of them exists.
     */
    public static PlaceTranslation resolveFrom(List<PlaceTranslation> translations, String requestedLocale) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }
        for (TranslationLocale locale : fallbackChain(requestedLocale)) {
            PlaceTranslation match = findLocale(translations, locale);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    /**
     * The place's name in {@code requestedLocale} ({@link #resolveFrom} chain) from an already
     * loaded list; null when the place has no translated name, and its title stands.
     */
    public static String localizedName(List<PlaceTranslation> translations, String requestedLocale) {
        PlaceTranslation resolved = resolveFrom(translations, requestedLocale);
        return resolved == null || resolved.getName() == null || resolved.getName().isBlank()
                ? null
                : resolved.getName();
    }

    /**
     * The name to show for text that was copied from a place's name when the place was picked:
     * an activity's name, a saved place's. It is the place's name in {@code requestedLocale}
     * while {@code storedName} is still one of the place's names (its title or any translation,
     * case and surrounding space aside), so a reader sees the place in their own language. Null
     * when the text was changed by hand since, which is then what the author meant to say, or
     * when the place has no translated name.
     */
    public static String localizedCopy(String storedName, String placeTitle,
                                       List<PlaceTranslation> translations, String requestedLocale) {
        String localized = localizedName(translations, requestedLocale);
        if (localized == null) {
            return null;
        }
        if (storedName == null || storedName.isBlank()) {
            return localized;
        }
        String stored = storedName.trim();
        if (placeTitle != null && stored.equalsIgnoreCase(placeTitle.trim())) {
            return localized;
        }
        for (PlaceTranslation translation : translations) {
            if (translation.getName() != null && stored.equalsIgnoreCase(translation.getName().trim())) {
                return localized;
            }
        }
        return null;
    }

    /** The {@code vi} row of a loaded list: the place's own name, shown beside a translated one. */
    public static PlaceTranslation original(List<PlaceTranslation> translations) {
        return translations == null ? null : findLocale(translations, TranslationLocale.DEFAULT);
    }

    static List<TranslationLocale> fallbackChain(String requestedLocale) {
        TranslationLocale locale = TranslationLocale.fromCode(requestedLocale).orElse(TranslationLocale.DEFAULT);
        List<TranslationLocale> chain = new ArrayList<>(4);
        chain.add(locale);
        locale.sibling().ifPresent(chain::add);
        for (TranslationLocale next : List.of(TranslationLocale.FALLBACK, TranslationLocale.DEFAULT)) {
            if (!chain.contains(next)) {
                chain.add(next);
            }
        }
        return chain;
    }

    private static PlaceTranslation findLocale(List<PlaceTranslation> translations, TranslationLocale locale) {
        for (PlaceTranslation translation : translations) {
            if (translation.getLocale() == locale) {
                return translation;
            }
        }
        return null;
    }

    /** The wire shape of one translation, as {@link #allResponses} returns it. */
    public static PlaceTranslationResponse responseOf(PlaceTranslation translation) {
        return PlaceTranslationResponse.builder()
                .locale(translation.getLocale().code())
                .name(translation.getName())
                .description(translation.getDescription())
                .translationSource(translation.getTranslationSource())
                .build();
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

    /** Clients may mark a name MANUAL or GOOGLE; blank or anything else is MANUAL. */
    private static TranslationSource requestSource(PlaceTranslationRequest request) {
        String value = request.getSource();
        return value != null && TranslationSource.GOOGLE.name().equalsIgnoreCase(value.trim())
                ? TranslationSource.GOOGLE
                : TranslationSource.MANUAL;
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
