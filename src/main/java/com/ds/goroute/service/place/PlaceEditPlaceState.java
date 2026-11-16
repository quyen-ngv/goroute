package com.ds.goroute.service.place;

import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.service.PlaceTranslationService;

import java.util.List;

/**
 * A place as a suggestion sees it: the stored row and its translations, with the name and the
 * description it shows in one language (place_translations first, then the row's own text).
 */
public record PlaceEditPlaceState(Place place, List<PlaceTranslation> translations, String locale) {

    public PlaceEditPlaceState {
        translations = translations == null ? List.of() : translations;
    }

    public String displayName() {
        PlaceTranslation translation = PlaceTranslationService.resolveFrom(translations, locale);
        return translation != null && translation.getName() != null && !translation.getName().isBlank()
                ? translation.getName()
                : place.getTitle();
    }

    public String displayDescription() {
        PlaceTranslation translation = PlaceTranslationService.resolveFrom(translations, locale);
        return translation != null && translation.getDescription() != null
                ? translation.getDescription()
                : place.getDescriptions();
    }

    /** The {@code vi} row's name, the place's own name; null when it has none. */
    public String originalName() {
        PlaceTranslation original = PlaceTranslationService.original(translations);
        return original == null ? null : original.getName();
    }
}
