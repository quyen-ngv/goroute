package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.entity.AiTripGenerationJob;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.service.AiTripGenerationService;
import com.ds.goroute.service.AiTripWorkerDispatcher;
import com.ds.goroute.service.SocialItineraryRequestFactory;
import com.ds.goroute.service.SocialSavedSpotService;
import com.ds.goroute.type.SocialLocationOperation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SocialLocationCompletionServiceImplTest {
    private final SocialSavedSpotService savedSpotService = mock(SocialSavedSpotService.class);
    private final SocialItineraryRequestFactory requestFactory = mock(SocialItineraryRequestFactory.class);
    private final AiTripGenerationService aiTripGenerationService = mock(AiTripGenerationService.class);
    private final AiTripWorkerDispatcher workerDispatcher = mock(AiTripWorkerDispatcher.class);
    private final SocialLocationJobMapper jobMapper = mock(SocialLocationJobMapper.class);
    private final SocialLocationCompletionServiceImpl service = new SocialLocationCompletionServiceImpl(
            savedSpotService, requestFactory, aiTripGenerationService, workerDispatcher, jobMapper);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void savesAllSpotsAndStartsTheNormalAiTripJobForItinerary() throws Exception {
        UUID socialJobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID aiJobId = UUID.randomUUID();
        SocialLocationJob job = SocialLocationJob.builder().id(socialJobId).userId(userId)
                .operation(SocialLocationOperation.GEN_ITINERARY).language("vi").build();
        JsonNode result = objectMapper.readTree("{\"extraction\":{\"contentType\":\"ITINERARY\"}}");
        AiTripGenerateRequest request = AiTripGenerateRequest.builder().cityName("Hue").build();
        AiTripGenerationJob aiJob = new AiTripGenerationJob();
        aiJob.setId(aiJobId);
        when(savedSpotService.saveFromJob(userId, socialJobId, result)).thenReturn(3);
        when(requestFactory.build(job, result)).thenReturn(request);
        when(aiTripGenerationService.create(eq(request), eq(userId), eq("social:" + socialJobId), eq("vi")))
                .thenReturn(aiJob);

        assertThat(service.handleCompleted(job, result)).isTrue();

        assertThat(job.getSavedSpotCount()).isEqualTo(3);
        assertThat(job.getAiTripJobId()).isEqualTo(aiJobId);
        verify(workerDispatcher).dispatch(aiJob);
        verify(jobMapper).update(job);
    }

    @Test
    void savesPlaceListButDoesNotReserveOrDispatchAiTrip() throws Exception {
        SocialLocationJob job = SocialLocationJob.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .operation(SocialLocationOperation.SAVE_SPOTS).build();
        JsonNode result = objectMapper.readTree("{\"extraction\":{\"contentType\":\"PLACE_LIST\"}}");
        when(savedSpotService.saveFromJob(any(), any(), eq(result))).thenReturn(2);

        assertThat(service.handleCompleted(job, result)).isFalse();

        assertThat(job.getSavedSpotCount()).isEqualTo(2);
        verifyNoInteractions(requestFactory, aiTripGenerationService, workerDispatcher);
        verify(jobMapper).update(job);
    }

    @Test
    void keepsSavedSpotsWhenAiTripQuotaRejectsTheItineraryProjection() throws Exception {
        UUID socialJobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SocialLocationJob job = SocialLocationJob.builder().id(socialJobId).userId(userId)
                .operation(SocialLocationOperation.GEN_ITINERARY).language("vi").build();
        JsonNode result = objectMapper.readTree("{\"extraction\":{\"contentType\":\"ITINERARY\"}}");
        AiTripGenerateRequest request = AiTripGenerateRequest.builder().cityName("Hue").build();
        when(savedSpotService.saveFromJob(userId, socialJobId, result)).thenReturn(2);
        when(requestFactory.build(job, result)).thenReturn(request);
        when(aiTripGenerationService.create(eq(request), eq(userId), eq("social:" + socialJobId), eq("vi")))
                .thenThrow(new RuntimeException("AI_TRIP_QUOTA_EXHAUSTED"));

        assertThat(service.handleCompleted(job, result)).isFalse();

        assertThat(job.getSavedSpotCount()).isEqualTo(2);
        assertThat(job.getAiTripJobId()).isNull();
        verify(savedSpotService).saveFromJob(userId, socialJobId, result);
        verify(jobMapper).update(job);
        verifyNoInteractions(workerDispatcher);
    }
}
