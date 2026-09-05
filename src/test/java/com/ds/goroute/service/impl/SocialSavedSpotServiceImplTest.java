package com.ds.goroute.service.impl;

import com.ds.goroute.entity.SocialSavedSpot;
import com.ds.goroute.mapper.SocialSavedSpotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SocialSavedSpotServiceImplTest {
    private final SocialSavedSpotMapper mapper = mock(SocialSavedSpotMapper.class);
    private final SocialSavedSpotServiceImpl service =
            new SocialSavedSpotServiceImpl(mapper, new ObjectMapper());

    @Test
    void persistsEveryNamedCandidateIncludingUnresolvedAndSeparateOptions() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        String result = """
                {"extraction":{"contentType":"ITINERARY","candidates":[
                  {"candidateRef":"social-0001","sequence":1,"name":"Unresolved point",
                   "description":"Mentioned in the video","resolutionStatus":"UNRESOLVED",
                   "address_hint":"Video address"},
                  {"candidateRef":"social-0002","sequence":2,"name":"Lunch A",
                   "optionGroupId":"lunch","optionIndex":1,"relation":"OPTION",
                   "identity_status":"CONFIRMED",
                   "mapSearch":{"candidates":[{"placeId":"google-1","latitude":16.1,"longitude":108.2,
                     "placeMapping":{"placeId":"PLACE_ID"}}]}},
                  {"candidateRef":"social-0003","sequence":3,"name":"Lunch B",
                   "optionGroupId":"lunch","optionIndex":2,"relation":"OPTION",
                   "resolutionStatus":"UNRESOLVED"}
                ]}}
                """.replace("PLACE_ID", placeId.toString());

        int saved = service.saveFromJob(userId, jobId, new ObjectMapper().readTree(result));

        assertThat(saved).isEqualTo(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SocialSavedSpot>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).upsertAll(captor.capture());
        List<SocialSavedSpot> spots = captor.getValue();
        assertThat(spots).extracting(SocialSavedSpot::getCandidateRef)
                .containsExactly("social-0001", "social-0002", "social-0003");
        assertThat(spots.get(0).getResolutionStatus()).isEqualTo("UNRESOLVED");
        assertThat(spots.get(0).getAddressHint()).isEqualTo("Video address");
        assertThat(spots.get(1).getPlaceId()).isEqualTo(placeId);
        assertThat(spots.get(1).getGooglePlaceId()).isEqualTo("google-1");
        assertThat(spots.get(1).getIdentityStatus()).isEqualTo("CONFIRMED");
        assertThat(spots.get(1).getOptionGroupId()).isEqualTo("lunch");
        assertThat(spots.get(1).getOptionIndex()).isEqualTo(1);
        assertThat(spots.get(2).getOptionIndex()).isEqualTo(2);
        assertThat(spots).allMatch(spot -> spot.getSequence() != null);
    }

    @Test
    void sendsLargeResultsInIndependentBatches() throws Exception {
        StringBuilder json = new StringBuilder("{\"extraction\":{\"candidates\":[");
        for (int i = 1; i <= 101; i++) {
            if (i > 1) json.append(',');
            json.append("{\"name\":\"Spot ").append(i).append("\"}");
        }
        json.append("]}}");

        int saved = service.saveFromJob(UUID.randomUUID(), UUID.randomUUID(),
                new ObjectMapper().readTree(json.toString()));

        assertThat(saved).isEqualTo(101);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SocialSavedSpot>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper, times(2)).upsertAll(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(100, 1);
    }

    @Test
    void listAndDeleteUseBoundedOwnedQueries() {
        UUID userId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        when(mapper.findByUserId(userId, 100, 300)).thenReturn(List.of());
        when(mapper.deleteOwned(userId, spotId)).thenReturn(1);

        assertThat(service.listMine(userId, 3, 500)).isEmpty();
        service.delete(userId, spotId);

        verify(mapper).findByUserId(userId, 100, 300);
        verify(mapper).deleteOwned(userId, spotId);
        verifyNoMoreInteractions(mapper);
    }
}
