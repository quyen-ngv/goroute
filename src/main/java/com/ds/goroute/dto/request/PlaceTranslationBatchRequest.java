package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Localized place names written by the name scraper and backfill. Rows are checked one by one:
 * an invalid row is counted in {@code skippedInvalid}, it does not reject the batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceTranslationBatchRequest {

    public static final int MAX_ITEMS = 500;

    @NotNull
    @Size(min = 1, max = MAX_ITEMS)
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** Google place id; looked up as a cid when no place has it as place id. */
        private String googlePlaceId;
        /** A {@code TranslationLocale} code, normalised like Accept-Language (pt-BR → pt). */
        private String locale;
        /** 1–500 characters after trimming. */
        private String name;
        /** GOOGLE, WIKIDATA or MANUAL. */
        private String source;
    }
}
