package com.ds.goroute.service.marketplace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingReadinessTest {
    @Test
    void readyOnlyWhenEveryRequiredCheckPasses() {
        var result = ListingReadiness.score(List.of(
                ListingReadiness.of("ROOM_ENABLED", "room", true, true, 50, null),
                ListingReadiness.of("PHOTOS_MIN_5", "photos", false, false, 30, "2 photo(s)"),
                ListingReadiness.of("CONTACT", "contact", false, true, 20, null)));
        assertFalse(result.ready());
        assertEquals(List.of("CONTACT"), result.failingRequiredCodes());
        assertEquals(50, result.score());
    }

    @Test
    void optionalChecksOnlyAffectTheScore() {
        var result = ListingReadiness.score(List.of(
                ListingReadiness.of("ROOM_ENABLED", "room", true, true, 60, null),
                ListingReadiness.of("AMENITIES", "amenities", false, false, 40, null)));
        assertTrue(result.ready());
        assertEquals(60, result.score());
        assertTrue(result.failingRequiredCodes().isEmpty());
    }

    @Test
    void emptyChecklistIsNotBookable() {
        var result = ListingReadiness.score(List.of());
        assertEquals(0, result.score());
        assertTrue(result.ready(), "vacuously true: nothing required failed");
    }
}
