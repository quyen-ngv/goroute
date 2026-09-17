package com.ds.goroute.utils;

import com.ds.goroute.entity.LocationImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocationAreaMatcherTest {

    /** Coordinates taken from the curated areas actually configured in production. */
    private static final LocationImage DA_NANG = area("Đà Nẵng", 16.0611415, 108.2229594, 30);
    private static final LocationImage HOI_AN = area("Hội An", 15.8770873, 108.3260704, 30);
    private static final LocationImage HUE = area("Huế", 16.469527, 107.577432, 30);
    private static final LocationImage CA_MAU = area("Cà Mau", 8.7477284, 104.7258168, 30);
    private static final LocationImage HA_NOI = area("Hà Nội", 21.0285, 105.8542, 30);

    private static final List<LocationImage> AREAS = List.of(DA_NANG, HOI_AN, HUE, CA_MAU, HA_NOI);

    @Nested
    @DisplayName("matching by coordinates")
    class ByCoordinates {

        @Test
        @DisplayName("picks the nearest area when radii overlap")
        void picksNearestWhenRadiiOverlap() {
            // Hoi An old town: inside Da Nang's 30km radius as well, but closer to Hoi An.
            Optional<LocationImage> matched = LocationAreaMatcher.matchByCoordinates(
                    AREAS, new BigDecimal("15.8801"), new BigDecimal("108.3380"));

            assertThat(matched).contains(HOI_AN);
        }

        @Test
        @DisplayName("returns empty when no area's radius reaches the point")
        void returnsEmptyOutsideEveryRadius() {
            // Ca Mau city, ~60km from the Ca Mau anchor pinned at the cape.
            Optional<LocationImage> matched = LocationAreaMatcher.matchByCoordinates(
                    AREAS, new BigDecimal("9.1769"), new BigDecimal("105.1500"));

            assertThat(matched).isEmpty();
        }

        @Test
        @DisplayName("honours a widened per-area radius")
        void honoursWidenedRadius() {
            LocationImage wideCaMau = area("Cà Mau", 8.7477284, 104.7258168, 80);

            Optional<LocationImage> matched = LocationAreaMatcher.matchByCoordinates(
                    List.of(wideCaMau), new BigDecimal("9.1769"), new BigDecimal("105.1500"));

            assertThat(matched).contains(wideCaMau);
        }

        @Test
        @DisplayName("falls back to 30km when an area has no radius configured")
        void defaultsRadiusWhenMissing() {
            LocationImage noRadius = area("Đà Nẵng", 16.0611415, 108.2229594, null);

            assertThat(LocationAreaMatcher.matchByCoordinates(
                    List.of(noRadius), new BigDecimal("16.07"), new BigDecimal("108.22")))
                    .contains(noRadius);
            assertThat(LocationAreaMatcher.matchByCoordinates(
                    List.of(noRadius), new BigDecimal("17.50"), new BigDecimal("108.22")))
                    .isEmpty();
        }

        @Test
        @DisplayName("returns empty for a row without coordinates")
        void returnsEmptyWithoutCoordinates() {
            assertThat(LocationAreaMatcher.matchByCoordinates(AREAS, null, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("matching by name")
    class ByName {

        @Test
        @DisplayName("matches an area named in a structured address")
        void matchesAreaNamedInAddress() {
            Optional<LocationImage> matched = LocationAreaMatcher.matchByText(
                    AREAS, List.of("116 Phạm Hồng Thắm, An Xuyên, Cà Mau, Vietnam"));

            assertThat(matched).contains(CA_MAU);
        }

        @Test
        @DisplayName("does not match an area whose name is only part of a longer word")
        void doesNotMatchInsideALongerWord() {
            // "Nhue" (a Hanoi river) contains "hue" as a substring but not as a token;
            // a substring match would file Hanoi rows under Hue.
            Optional<LocationImage> matched = LocationAreaMatcher.matchByText(
                    AREAS, List.of("Đường ven sông Nhuệ, Hà Đông"));

            assertThat(matched).isEmpty();
        }

        @Test
        @DisplayName("prefers the longest area name when several match")
        void prefersLongestName() {
            // A business name carrying "Huệ" alongside a real Ca Mau address must not
            // outrank the address.
            Optional<LocationImage> matched = LocationAreaMatcher.matchByText(
                    AREAS, List.of("100 Lý Thường Kiệt, Hòa Thành, Cà Mau", "Quán Bánh Tầm Cô Huệ"));

            assertThat(matched).contains(CA_MAU);
        }

        @Test
        @DisplayName("ignores accents and separators")
        void ignoresAccentsAndSeparators() {
            assertThat(LocationAreaMatcher.matchByText(AREAS, List.of("ha noi"))).contains(HA_NOI);
            assertThat(LocationAreaMatcher.matchByText(AREAS, List.of("HÀ NỘI"))).contains(HA_NOI);
            assertThat(LocationAreaMatcher.matchByText(AREAS, List.of("quan-1--ha.noi"))).contains(HA_NOI);
        }

        @Test
        @DisplayName("returns empty for text naming no area")
        void returnsEmptyForUnknownText() {
            assertThat(LocationAreaMatcher.matchByText(AREAS, List.of("Vientiane, Laos"))).isEmpty();
            assertThat(LocationAreaMatcher.matchByText(AREAS, List.of(""))).isEmpty();
        }
    }

    @Nested
    @DisplayName("combined matching")
    class Combined {

        @Test
        @DisplayName("prefers coordinates over the name in the text")
        void prefersCoordinates() {
            // Address text says Ha Noi, coordinates say Hoi An. Coordinates win, because
            // a stale or copy-pasted address is the more common error.
            Optional<LocationImage> matched = LocationAreaMatcher.match(
                    AREAS, new BigDecimal("15.8801"), new BigDecimal("108.3380"),
                    List.of("somewhere in Hà Nội"));

            assertThat(matched).contains(HOI_AN);
        }

        @Test
        @DisplayName("falls back to the name when coordinates resolve nothing")
        void fallsBackToName() {
            Optional<LocationImage> matched = LocationAreaMatcher.match(
                    AREAS, new BigDecimal("9.1769"), new BigDecimal("105.1500"),
                    List.of("An Xuyên, Cà Mau, Vietnam"));

            assertThat(matched).contains(CA_MAU);
        }
    }

    @Test
    @DisplayName("normalize strips accents, đ and punctuation but keeps token boundaries")
    void normalizeKeepsTokenBoundaries() {
        assertThat(LocationAreaMatcher.normalize("Đà Nẵng")).isEqualTo("da nang");
        assertThat(LocationAreaMatcher.normalize("  Hồ Chí Minh!! ")).isEqualTo("ho chi minh");
        assertThat(LocationAreaMatcher.normalize(null)).isEmpty();
    }

    private static LocationImage area(String name, double lat, double lng, Integer radiusKm) {
        return LocationImage.builder()
                .id(UUID.randomUUID())
                .fullAddress(name)
                .latitude(BigDecimal.valueOf(lat))
                .longitude(BigDecimal.valueOf(lng))
                .coverageRadiusKm(radiusKm == null ? null : BigDecimal.valueOf(radiusKm))
                .priority(0)
                .build();
    }
}
