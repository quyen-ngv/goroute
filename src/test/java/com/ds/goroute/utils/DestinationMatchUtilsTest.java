package com.ds.goroute.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestinationMatchUtilsTest {

    @Test
    void matchesAccentAndSpacingVariants() {
        List<String> booking = List.of("Ha Noi");
        assertTrue(DestinationMatchUtils.matches(booking, List.of("Ha ná»™i")));
        assertTrue(DestinationMatchUtils.matches(booking, List.of("hanoi")));
        assertTrue(DestinationMatchUtils.matches(booking, List.of("HÃ€ Ná»˜I")));
    }

    @Test
    void matchesFreeTextContainingDestination() {
        List<String> booking = List.of("Ha Noi");
        assertTrue(DestinationMatchUtils.matches(booking, List.of("abc phÆ°Æ¡ng xyz hÃ  ná»™i")));
    }

    @Test
    void doesNotMatchUnrelatedDestination() {
        List<String> booking = List.of("Ha Noi");
        assertFalse(DestinationMatchUtils.matches(booking, List.of("Da Nang")));
        assertFalse(DestinationMatchUtils.matches(booking, List.of("abc xyz")));
    }
}
