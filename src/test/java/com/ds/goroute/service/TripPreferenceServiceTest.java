package com.ds.goroute.service;

import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.request.TripPreferencesRequest;
import com.ds.goroute.entity.TripPreference;
import com.ds.goroute.mapper.TripPreferenceMapper;
import com.ds.goroute.type.PlaceGroup;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TripPreferenceServiceTest {
    private final TripPreferenceMapper mapper = mock(TripPreferenceMapper.class);
    private final TripPreferenceService service = new TripPreferenceService(mapper);
    private final UUID tripId = UUID.randomUUID();

    @Test
    void storesUpperCaseCodesAndDropsUnknownValues() {
        service.save(tripId, TripPreferencesRequest.builder()
                .companion("family").withChildren(true).pace("relaxed").discoveryStyle("unknown")
                .interests(List.of("FOOD_AND_DRINK", "Adventure", "Adventure", "ăn ngon"))
                .dietary(List.of("no_pork", "vegan"))
                .dietaryNote("  ít cay  ").preferenceText(" ")
                .build(), TripPreference.SOURCE_MANUAL);

        ArgumentCaptor<TripPreference> row = ArgumentCaptor.forClass(TripPreference.class);
        verify(mapper).upsert(row.capture());
        assertThat(row.getValue().getCompanion()).isEqualTo("FAMILY");
        assertThat(row.getValue().isWithChildren()).isTrue();
        assertThat(row.getValue().isWithElderly()).isFalse();
        assertThat(row.getValue().getPace()).isEqualTo("RELAXED");
        assertThat(row.getValue().getDiscoveryStyle()).as("unknown code is not stored").isNull();
        assertThat(row.getValue().getDietaryNote()).isEqualTo("ít cay");
        assertThat(row.getValue().getPreferenceText()).isNull();
        assertThat(row.getValue().getSource()).isEqualTo("MANUAL");
        verify(mapper).deleteTags(tripId);
        verify(mapper).insertTags(tripId, "INTEREST", List.of("FOOD_AND_DRINK", "ADVENTURE"));
        verify(mapper).insertTags(tripId, "DIETARY", List.of("NO_PORK", "VEGAN"));
    }

    @Test
    void noPreferencesWritesNothing() {
        service.save(tripId, null, TripPreference.SOURCE_MANUAL);

        verifyNoInteractions(mapper);
    }

    @Test
    void emptyTagListsAreNotInserted() {
        service.save(tripId, TripPreferencesRequest.builder().pace("EAGER").build(), TripPreference.SOURCE_AI);

        verify(mapper).upsert(any());
        verify(mapper, never()).insertTags(eq(tripId), any(), anyList());
    }

    @Test
    void olderAiRequestsAreRebuiltFromPromptFields() {
        AiTripGenerateRequest request = AiTripGenerateRequest.builder()
                .pace("EAGER").discoveryStyle("HIDDEN_GEMS")
                .placeGroups(List.of(PlaceGroup.FOOD_AND_DRINK))
                .activityTypes(List.of("Photography"))
                .mobilityConsiderations(List.of("Kid-friendly"))
                .dietaryRestrictions(List.of("halal"))
                .preferenceText("không dậy sớm")
                .build();

        TripPreferencesRequest prefs = service.fromAiRequest(request);

        assertThat(prefs.getPace()).isEqualTo("EAGER");
        assertThat(prefs.getInterests()).containsExactly("FOOD_AND_DRINK", "Photography");
        assertThat(prefs.getWithChildren()).isTrue();
        assertThat(prefs.getWithElderly()).isFalse();
        assertThat(prefs.getDietary()).containsExactly("halal");
        assertThat(prefs.getPreferenceText()).isEqualTo("không dậy sớm");
    }

    @Test
    void structuredAiPreferencesWinAndSocialVideoHasNone() {
        TripPreferencesRequest structured = TripPreferencesRequest.builder().companion("COUPLE").build();
        assertThat(service.fromAiRequest(AiTripGenerateRequest.builder().preferences(structured).build()))
                .isSameAs(structured);
        assertThat(service.fromAiRequest(AiTripGenerateRequest.builder().socialContext(Map.of("jobId", "x")).build()))
                .isNull();
    }
}
