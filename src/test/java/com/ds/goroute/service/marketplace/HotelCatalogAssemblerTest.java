package com.ds.goroute.service.marketplace;

import com.ds.goroute.dto.response.HotelAmenityGroupResponse;
import com.ds.goroute.dto.response.HotelProfileResponse;
import com.ds.goroute.entity.HotelProfile;
import com.ds.goroute.entity.PlaceScore;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.service.ExchangeRateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HotelCatalogAssemblerTest {
    private final PlaceScoreRepository scores = mock(PlaceScoreRepository.class);
    private final HotelCatalogAssembler assembler = new HotelCatalogAssembler(
            new MarketplaceJson(new ObjectMapper().findAndRegisterModules()), scores,
            new MarketplaceDisplayPrice(mock(ExchangeRateService.class)));

    @Test
    @DisplayName("Known amenity codes are grouped in section order; free text lands in OTHER")
    void groupsAmenities() {
        List<HotelAmenityGroupResponse> groups = HotelCatalogAssembler.groupAmenities(
                List.of("swimming_pool", "WIFI", "Rooftop cinema", "CCTV", "WIFI"));

        assertThat(groups).extracting(HotelAmenityGroupResponse::group)
                .containsExactly("GENERAL", "WELLNESS", "SAFETY", "OTHER");
        assertThat(groups.get(0).amenities()).containsExactly("WIFI");
        assertThat(groups.get(3).amenities()).containsExactly("Rooftop cinema");
        assertThat(HotelCatalogAssembler.popularAmenities(List.of("CCTV", "wifi", "SWIMMING_POOL")))
                .containsExactly("WIFI", "SWIMMING_POOL");
    }

    @Test
    @DisplayName("The gallery prefers the property's own photos and falls back to the place's")
    void gallery() {
        assertThat(HotelCatalogAssembler.gallery(List.of("a.jpg"), "thumb.jpg", List.of("p.jpg"))).containsExactly("a.jpg");
        assertThat(HotelCatalogAssembler.gallery(List.of(), "thumb.jpg", List.of("p.jpg", "thumb.jpg", " ")))
                .containsExactly("thumb.jpg", "p.jpg");
    }

    @Test
    @DisplayName("Public detail uses in-app review aspects when there are reviews, else the imported rating")
    void reviewSummary() {
        UUID withReviews = UUID.randomUUID();
        when(scores.findByPlaceId(withReviews)).thenReturn(Optional.of(PlaceScore.builder().placeId(withReviews)
                .tripmindScore(new BigDecimal("4.6")).reviewCount(12).locationScore(new BigDecimal("4.8"))
                .cleanlinessScore(new BigDecimal("4.5")).build()));
        HotelProfileResponse detail = assembler.toPublicDetail(hotel(withReviews).placeReviewRating(new BigDecimal("4.1")).build());
        assertThat(detail.getReviewSummary().source()).isEqualTo("GOROUTE");
        assertThat(detail.getReviewSummary().locationScore()).isEqualByComparingTo("4.8");

        UUID imported = UUID.randomUUID();
        when(scores.findByPlaceId(imported)).thenReturn(Optional.empty());
        HotelProfileResponse external = assembler.toPublicDetail(hotel(imported).placeReviewRating(new BigDecimal("4.1")).placeReviewCount(900).build());
        assertThat(external.getReviewSummary().source()).isEqualTo("EXTERNAL");
        assertThat(external.getReviewSummary().reviewCount()).isEqualTo(900);
        assertThat(external.getReviewSummary().locationScore()).isNull();
    }

    @Test
    @DisplayName("Legacy policy keys still read, unknown keys are ignored")
    void readsLegacyPolicies() {
        HotelProfileResponse response = assembler.toResponse(hotel(UUID.randomUUID())
                .policies("{\"petsAllowed\":true,\"checkInInstructions\":\"Ring the bell\",\"legacyKey\":1}").build());
        assertThat(response.getPolicies().getPetsAllowed()).isTrue();
        assertThat(response.getPolicies().getCheckInInstructions()).isEqualTo("Ring the bell");
    }

    private static HotelProfile.HotelProfileBuilder hotel(UUID placeId) {
        return HotelProfile.builder().id(UUID.randomUUID()).placeId(placeId).amenities("[]").images("[]");
    }
}
