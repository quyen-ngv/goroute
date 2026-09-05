package com.ds.goroute.service.impl;

import com.ds.goroute.config.InternalApiProperties;
import com.ds.goroute.dto.request.SocialLocationJobCallbackRequest;
import com.ds.goroute.dto.request.CreateSocialLocationJobRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AiApiCallMapper;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.SocialLocationRestrictionRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PlaceImportJobService;
import com.ds.goroute.service.PlaceSocialVideoService;
import com.ds.goroute.service.SocialLocationConfigService;
import com.ds.goroute.service.SocialLocationCompletionService;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.thirdparty.scrape.ScrapeSocialLocationJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeSocialLocationJobResponse;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.SocialLocationJobStatus;
import com.ds.goroute.type.SocialLocationOperation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SocialLocationJobServiceImplTest {
    private SocialLocationJobMapper jobMapper;
    private AiApiCallMapper aiApiCallMapper;
    private PlaceImportJobMapper placeImportJobMapper;
    private PlaceMapper placeMapper;
    private PlaceImportJobService placeImportJobService;
    private PlaceSocialVideoService placeSocialVideoService;
    private NotificationService notificationService;
    private ScrapeServiceClient scrapeServiceClient;
    private SocialLocationConfigService socialConfigService;
    private AiTripRepository aiTripRepository;
    private SocialLocationRestrictionRepository restrictionRepository;
    private SocialLocationCompletionService completionService;
    private ObjectMapper objectMapper;
    private SocialLocationJobServiceImpl service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(SocialLocationJobMapper.class);
        aiApiCallMapper = mock(AiApiCallMapper.class);
        placeImportJobMapper = mock(PlaceImportJobMapper.class);
        placeMapper = mock(PlaceMapper.class);
        placeImportJobService = mock(PlaceImportJobService.class);
        placeSocialVideoService = mock(PlaceSocialVideoService.class);
        notificationService = mock(NotificationService.class);
        scrapeServiceClient = mock(ScrapeServiceClient.class);
        socialConfigService = mock(SocialLocationConfigService.class);
        aiTripRepository = mock(AiTripRepository.class);
        restrictionRepository = mock(SocialLocationRestrictionRepository.class);
        completionService = mock(SocialLocationCompletionService.class);
        objectMapper = new ObjectMapper();
        service = new SocialLocationJobServiceImpl(
                jobMapper,
                aiApiCallMapper,
                placeImportJobMapper,
                placeMapper,
                scrapeServiceClient,
                objectMapper,
                placeImportJobService,
                placeSocialVideoService,
                socialConfigService,
                aiTripRepository,
                restrictionRepository,
                notificationService,
                completionService,
                new InternalApiProperties("ai-token", "internal-token")
        );
        ReflectionTestUtils.setField(service, "dispatchTimeoutSeconds", 90L);
        ReflectionTestUtils.setField(service, "jobTimeoutMinutes", 15L);
        ReflectionTestUtils.setField(service, "reconcileIntervalSeconds", 20L);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "internalBaseUrl", "http://goroute-app:8080");
        when(jobMapper.findStaleDispatching(any(), anyInt())).thenReturn(List.of());
        when(jobMapper.findProcessingForReconciliation(any(), anyInt())).thenReturn(List.of());
        when(jobMapper.updateIfStatus(any(), any())).thenReturn(1);
        when(restrictionRepository.findByUserId(any())).thenReturn(Optional.empty());
    }

    @Test
    void createInvalidatesCompletedResultFromLegacyCandidatePolicy() {
        UUID userId = UUID.randomUUID();
        UUID legacyJobId = UUID.randomUUID();
        String sourceUrl = "https://www.tiktok.com/@user/video/123";
        SocialLocationJob legacyJob = SocialLocationJob.builder()
                .id(legacyJobId)
                .userId(userId)
                .sourceUrl(sourceUrl)
                .sourceKey("tiktok:123")
                .platform("tiktok")
                .status(SocialLocationJobStatus.COMPLETED)
                .resultPayload("{\"extraction\":{\"candidates\":[{\"name\":\"guessed place\"}]}}")
                .build();
        when(jobMapper.findReusableByUserIdAndSourceKey(eq(userId), any())).thenReturn(legacyJob);
        when(jobMapper.markDeletedByIdAndUserId(legacyJobId, userId)).thenReturn(1);
        when(socialConfigService.dailyJobLimit(userId)).thenReturn(10);
        when(socialConfigService.maxQueuedJobs()).thenReturn(100);
        when(socialConfigService.maxVideoSeconds("FREE")).thenReturn(180);
        when(aiTripRepository.getSubscriptionTier(userId)).thenReturn("FREE");

        service.create(userId, CreateSocialLocationJobRequest.builder()
                .url(sourceUrl)
                .build());

        verify(jobMapper).markDeletedByIdAndUserId(legacyJobId, userId);
        ArgumentCaptor<SocialLocationJob> inserted = ArgumentCaptor.forClass(SocialLocationJob.class);
        verify(jobMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getId()).isNotEqualTo(legacyJobId);
        assertThat(inserted.getValue().getStatus()).isEqualTo(SocialLocationJobStatus.QUEUED);
        verify(jobMapper, never()).countCreatedByUserSince(eq(userId), any());
    }

    @Test
    void saveSpotsStillChecksQuotaWhenAnOldCandidatePolicyMustBeReprocessed() {
        UUID userId = UUID.randomUUID();
        UUID legacyJobId = UUID.randomUUID();
        String sourceUrl = "https://www.tiktok.com/@user/video/legacy";
        SocialLocationJob legacyJob = SocialLocationJob.builder()
                .id(legacyJobId)
                .userId(userId)
                .sourceUrl(sourceUrl)
                .sourceKey("tiktok:legacy")
                .platform("tiktok")
                .status(SocialLocationJobStatus.COMPLETED)
                .resultPayload("{\"extraction\":{\"candidates\":[]}}")
                .build();
        when(jobMapper.findReusableByUserIdAndSourceKey(eq(userId), any())).thenReturn(legacyJob);
        when(socialConfigService.dailyJobLimit(userId)).thenReturn(1);
        when(jobMapper.countCreatedByUserSince(eq(userId), any())).thenReturn(1);

        assertThatThrownBy(() -> service.create(userId, CreateSocialLocationJobRequest.builder()
                .url(sourceUrl)
                .operation(SocialLocationOperation.SAVE_SPOTS)
                .build()))
                .isInstanceOf(BusinessException.class);

        verify(jobMapper).countCreatedByUserSince(eq(userId), any());
        verify(jobMapper, never()).insert(any());
    }

    @Test
    void createReusesCompletedResultFromCurrentCandidatePolicy() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String sourceUrl = "https://www.tiktok.com/@user/video/123";
        SocialLocationJob currentJob = SocialLocationJob.builder()
                .id(jobId)
                .userId(userId)
                .sourceUrl(sourceUrl)
                .sourceKey("tiktok:123")
                .platform("tiktok")
                .status(SocialLocationJobStatus.COMPLETED)
                .resultPayload("""
                        {"extraction":{"candidateValidation":{"policy":"AI_EVIDENCE_JUDGE_V2"}}}
                        """)
                .build();
        when(jobMapper.findReusableByUserIdAndSourceKey(eq(userId), any())).thenReturn(currentJob);

        var response = service.create(userId, CreateSocialLocationJobRequest.builder()
                .url(sourceUrl)
                .build());

        assertThat(response.getId()).isEqualTo(jobId);
        verify(jobMapper, never()).markDeletedByIdAndUserId(any(), any());
        verify(jobMapper, never()).insert(any());
    }

    @Test
    void saveSpotsChecksSocialQuotaButItineraryDoesNot() {
        UUID userId = UUID.randomUUID();
        String sourceUrl = "https://www.instagram.com/reel/123";
        when(jobMapper.findReusableByUserIdAndSourceKey(eq(userId), any())).thenReturn(null);
        when(jobMapper.countCreatedByUserSince(eq(userId), any())).thenReturn(0);
        when(jobMapper.countQueued()).thenReturn(0);
        when(socialConfigService.dailyJobLimit(userId)).thenReturn(5);
        when(socialConfigService.maxQueuedJobs()).thenReturn(100);
        when(socialConfigService.maxVideoSeconds("FREE")).thenReturn(180);
        when(aiTripRepository.getSubscriptionTier(userId)).thenReturn("FREE");

        service.create(userId, CreateSocialLocationJobRequest.builder()
                .url(sourceUrl).operation(SocialLocationOperation.SAVE_SPOTS).build());

        verify(jobMapper).countCreatedByUserSince(eq(userId), any());
        ArgumentCaptor<SocialLocationJob> saveJob = ArgumentCaptor.forClass(SocialLocationJob.class);
        verify(jobMapper).insert(saveJob.capture());
        assertThat(saveJob.getValue().getOperation()).isEqualTo(SocialLocationOperation.SAVE_SPOTS);

        reset(jobMapper);
        when(jobMapper.findReusableByUserIdAndSourceKey(eq(userId), any())).thenReturn(null);
        when(jobMapper.countQueued()).thenReturn(0);
        service.create(userId, CreateSocialLocationJobRequest.builder()
                .url("https://www.instagram.com/reel/456")
                .operation(SocialLocationOperation.GEN_ITINERARY).build());

        verify(jobMapper, never()).countCreatedByUserSince(any(), any());
    }

    @Test
    void cacheHitProjectsRequestedOperationWithoutCallingWorkerOrQuota() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String sourceUrl = "https://www.tiktok.com/@creator/video/123";
        SocialLocationJob cached = SocialLocationJob.builder()
                .id(jobId).userId(userId).sourceUrl(sourceUrl).sourceKey("tiktok:123")
                .platform("tiktok").status(SocialLocationJobStatus.COMPLETED)
                .resultPayload("{\"extraction\":{\"candidateValidation\":{\"policy\":\"AI_EVIDENCE_JUDGE_V2\"},\"candidates\":[]}}")
                .build();
        when(jobMapper.findReusableByUserIdAndSourceKey(eq(userId), any())).thenReturn(cached);

        var response = service.create(userId, CreateSocialLocationJobRequest.builder()
                .url(sourceUrl).operation(SocialLocationOperation.SAVE_SPOTS).build());

        assertThat(response.getOperation()).isEqualTo(SocialLocationOperation.SAVE_SPOTS);
        verify(completionService).handleCompleted(eq(cached), any(JsonNode.class));
        verify(jobMapper, never()).countCreatedByUserSince(any(), any());
        verify(jobMapper, never()).insert(any());
        verifyNoInteractions(scrapeServiceClient);
    }

    @Test
    void inProgressCacheHitPersistsLatestProjectionChoice() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String sourceUrl = "https://www.instagram.com/reel/123";
        SocialLocationJob running = SocialLocationJob.builder()
                .id(jobId).userId(userId).sourceUrl(sourceUrl).sourceKey("instagram:123")
                .platform("instagram").status(SocialLocationJobStatus.PROCESSING)
                .operation(SocialLocationOperation.GEN_ITINERARY)
                .resultPayload("{\"extraction\":{\"candidates\":[]}}")
                .build();
        when(jobMapper.findReusableByUserIdAndSourceKey(eq(userId), any())).thenReturn(running);

        var response = service.create(userId, CreateSocialLocationJobRequest.builder()
                .url(sourceUrl).operation(SocialLocationOperation.SAVE_SPOTS).build());

        assertThat(response.getOperation()).isEqualTo(SocialLocationOperation.SAVE_SPOTS);
        assertThat(running.getOperation()).isEqualTo(SocialLocationOperation.SAVE_SPOTS);
        verify(jobMapper).update(running);
        verify(jobMapper, never()).countCreatedByUserSince(any(), any());
        verify(jobMapper, never()).insert(any());
        verifyNoInteractions(scrapeServiceClient);
    }

    @Test
    void getKeepsUnresolvedCandidatesForReview() {
        UUID userId = UUID.randomUUID();
        UUID legacyJobId = UUID.randomUUID();
        UUID currentJobId = UUID.randomUUID();
        SocialLocationJob legacyJob = SocialLocationJob.builder()
                .id(legacyJobId)
                .userId(userId)
                .status(SocialLocationJobStatus.COMPLETED)
                .resultPayload("{\"extraction\":{\"candidates\":[{\"name\":\"guessed\"}]}}")
                .build();
        SocialLocationJob currentJob = SocialLocationJob.builder()
                .id(currentJobId)
                .userId(userId)
                .status(SocialLocationJobStatus.COMPLETED)
                .resultPayload("""
                        {"extraction":{
                          "candidateValidation":{"policy":"AI_EVIDENCE_JUDGE_V2"},
                          "candidates":[
                            {"name":"certain","verification":{"status":"VERIFIED","policy":"AI_EVIDENCE_JUDGE_V2"}},
                            {"name":"unverified"}
                          ]
                        }}
                        """)
                .build();
        when(jobMapper.findById(legacyJobId)).thenReturn(legacyJob);
        when(jobMapper.findById(currentJobId)).thenReturn(currentJob);

        var legacyResponse = service.get(userId, legacyJobId);
        var currentResponse = service.get(userId, currentJobId);

        assertThat(legacyResponse.getResult().path("extraction").path("candidates")).hasSize(1);
        JsonNode currentCandidates = currentResponse.getResult().path("extraction").path("candidates");
        assertThat(currentCandidates.size()).isEqualTo(2);
        assertThat(currentCandidates.get(0).path("name").asText()).isEqualTo("certain");
        assertThat(currentCandidates.get(1).path("name").asText()).isEqualTo("unverified");
    }

    @Test
    void completedCallbackNotifiesOwnerWithExtractedPlaceCountAndPlatform() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SocialLocationJob job = processingJob(jobId, userId, "tiktok");
        when(jobMapper.findById(jobId)).thenReturn(job);
        when(placeImportJobMapper.findSocialItemsBySocialJobId(jobId)).thenReturn(List.of());

        service.handleCallback(SocialLocationJobCallbackRequest.builder()
                .gorouteJobId(jobId)
                .status("COMPLETED")
                .result(objectMapper.readTree("""
                        {"extraction":{
                          "usage":{"inputTokens":120,"outputTokens":30,"totalTokens":150},
                          "candidateValidation":{"policy":"AI_EVIDENCE_JUDGE_V2"},
                          "candidates":[
                            {"name":"A","verification":{"status":"VERIFIED","policy":"AI_EVIDENCE_JUDGE_V2"}},
                            {"name":"B","verification":{"status":"VERIFIED","policy":"AI_EVIDENCE_JUDGE_V2"}},
                            {"name":"C","verification":{"status":"VERIFIED","policy":"AI_EVIDENCE_JUDGE_V2"}}
                          ]
                        }}
                        """))
                .build());

        assertThat(job.getStatus()).isEqualTo(SocialLocationJobStatus.COMPLETED);
        verify(jobMapper).update(job);
        ArgumentCaptor<CreateSocialPlaceImportJobRequest> importRequest =
                ArgumentCaptor.forClass(CreateSocialPlaceImportJobRequest.class);
        verify(placeImportJobService).createFromSocialJobs(eq(userId), importRequest.capture());
        assertThat(importRequest.getValue().getMaxReviews()).isEqualTo(5);
        assertThat(importRequest.getValue().getLimit()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).createNotification(
                eq(userId),
                isNull(),
                eq(NotificationType.SOCIAL_PLACES_EXTRACTED),
                isNull(),
                isNull(),
                dataCaptor.capture(),
                isNull()
        );
        assertThat(dataCaptor.getValue())
                .containsEntry("placeCount", "3")
                .containsEntry("platform", "tiktok")
                .containsEntry("platformName", "TikTok")
                .containsEntry("socialJobId", jobId.toString())
                .containsEntry("deepLink", "/profile/saved");

        ArgumentCaptor<AiApiCall> aiCallCaptor = ArgumentCaptor.forClass(AiApiCall.class);
        verify(aiApiCallMapper).upsert(aiCallCaptor.capture());
        assertThat(aiCallCaptor.getValue().getFeature()).isEqualTo("SOCIAL_LOCATION");
        assertThat(aiCallCaptor.getValue().getOperation()).isEqualTo("EXTRACT_PLACES");
        assertThat(aiCallCaptor.getValue().getCandidateCount()).isEqualTo(3);
        assertThat(aiCallCaptor.getValue().getInputTokens()).isEqualTo(120);
        assertThat(aiCallCaptor.getValue().getOutputTokens()).isEqualTo(30);
        assertThat(aiCallCaptor.getValue().getTotalTokens()).isEqualTo(150);
        assertThat(aiCallCaptor.getValue().getResponsePayload()).contains("\"name\":\"A\"");
    }

    @Test
    void itineraryCompletionSuppressesExtractionNotificationWhenProjectionStartsAiTrip() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SocialLocationJob job = processingJob(jobId, userId, "tiktok");
        job.setOperation(SocialLocationOperation.GEN_ITINERARY);
        when(jobMapper.findById(jobId)).thenReturn(job);
        when(completionService.handleCompleted(eq(job), any(JsonNode.class))).thenReturn(true);

        service.handleCallback(SocialLocationJobCallbackRequest.builder()
                .gorouteJobId(jobId).status("COMPLETED")
                .result(objectMapper.readTree("""
                        {"extraction":{"contentType":"ITINERARY","candidateValidation":{"policy":"AI_EVIDENCE_JUDGE_V2"},"candidates":[{"name":"A"}]}}
                        """))
                .build());

        verify(notificationService, never()).createNotification(
                any(), any(), eq(NotificationType.SOCIAL_PLACES_EXTRACTED), any(), any(), any(), any());
        verify(completionService).handleCompleted(eq(job), any(JsonNode.class));
    }

    @Test
    void repeatedTerminalCallbackDoesNotCreateDuplicateNotification() {
        UUID jobId = UUID.randomUUID();
        SocialLocationJob job = processingJob(jobId, UUID.randomUUID(), "instagram");
        job.setStatus(SocialLocationJobStatus.COMPLETED);
        when(jobMapper.findById(jobId)).thenReturn(job);
        when(placeImportJobMapper.findSocialItemsBySocialJobId(jobId)).thenReturn(List.of());

        service.handleCallback(SocialLocationJobCallbackRequest.builder()
                .gorouteJobId(jobId)
                .status("COMPLETED")
                .build());

        verify(notificationService, never()).createNotification(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reconcilesCompletedWorkerJobWhenCallbackWasLost() {
        UUID jobId = UUID.randomUUID();
        SocialLocationJob job = processingJob(jobId, UUID.randomUUID(), "tiktok");
        job.setPythonJobId("python-123");
        job.setAttemptCount(1);
        job.setDeadlineAt(LocalDateTime.now().plusMinutes(5));
        when(jobMapper.tryDispatchLock()).thenReturn(true);
        when(jobMapper.findProcessingForReconciliation(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.findById(jobId)).thenReturn(job);
        when(placeImportJobMapper.findSocialItemsBySocialJobId(jobId)).thenReturn(List.of());
        when(scrapeServiceClient.pollJobData("python-123")).thenReturn(Map.of(
                "status", "completed",
                "result", Map.of(
                        "success", true,
                        "extraction", Map.of(
                                "success", true,
                                "extraction", Map.of(
                                        "candidateValidation", Map.of(
                                                "policy", "AI_EVIDENCE_JUDGE_V2"),
                                        "candidates", List.of(Map.of(
                                                "name", "A",
                                                "verification", Map.of(
                                                        "status", "VERIFIED",
                                                        "policy", "AI_EVIDENCE_JUDGE_V2"))))
                        )
                )
        ));

        service.dispatchQueuedJobs();

        assertThat(job.getStatus()).isEqualTo(SocialLocationJobStatus.COMPLETED);
        verify(placeImportJobService).createFromSocialJobs(eq(job.getUserId()), any());
        verify(notificationService).createNotification(
                eq(job.getUserId()), isNull(), eq(NotificationType.SOCIAL_PLACES_EXTRACTED),
                isNull(), isNull(), any(), isNull());
    }

    @Test
    void processingJobBecomesTimedOutAfterLastAttempt() {
        UUID jobId = UUID.randomUUID();
        SocialLocationJob job = processingJob(jobId, UUID.randomUUID(), "instagram");
        job.setPythonJobId("lost-python-job");
        job.setAttemptCount(2);
        job.setDeadlineAt(LocalDateTime.now().minusSeconds(1));
        when(jobMapper.tryDispatchLock()).thenReturn(true);
        when(jobMapper.findProcessingForReconciliation(any(), anyInt())).thenReturn(List.of(job));
        when(jobMapper.findById(jobId)).thenReturn(job);
        when(scrapeServiceClient.pollJobData("lost-python-job")).thenReturn(null);

        service.dispatchQueuedJobs();

        assertThat(job.getStatus()).isEqualTo(SocialLocationJobStatus.TIMED_OUT);
        assertThat(job.getErrorCode()).isEqualTo("WORKER_TIMEOUT");
        assertThat(job.getCompletedAt()).isNotNull();
        verify(jobMapper).updateIfStatus(job, "PROCESSING");
    }

    @Test
    void dispatchDoesNotLimitExtractedCandidateCount() {
        UUID jobId = UUID.randomUUID();
        SocialLocationJob job = processingJob(jobId, UUID.randomUUID(), "tiktok");
        job.setStatus(SocialLocationJobStatus.DISPATCHING);
        job.setMaxDurationSeconds(180);

        ScrapeSocialLocationJobResponse trigger = new ScrapeSocialLocationJobResponse();
        trigger.setJobId("python-job-1");
        when(jobMapper.tryDispatchLock()).thenReturn(true);
        when(jobMapper.countActive()).thenReturn(0);
        when(jobMapper.claimQueued(1)).thenReturn(List.of(job));
        when(jobMapper.findById(jobId)).thenReturn(job);
        when(socialConfigService.maxConcurrentJobs()).thenReturn(1);
        when(socialConfigService.frameIntervalSeconds()).thenReturn(3);
        when(socialConfigService.imageMaxWidth()).thenReturn(448);
        when(socialConfigService.imageJpegQuality()).thenReturn(12);
        when(socialConfigService.aiProvider()).thenReturn("OPENAI");
        when(socialConfigService.aiModel()).thenReturn("gpt-5-mini");
        when(socialConfigService.aiBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(scrapeServiceClient.triggerSocialLocationJob(any())).thenReturn(trigger);

        service.dispatchQueuedJobs();

        ArgumentCaptor<ScrapeSocialLocationJobRequest> requestCaptor =
                ArgumentCaptor.forClass(ScrapeSocialLocationJobRequest.class);
        verify(scrapeServiceClient).triggerSocialLocationJob(requestCaptor.capture());
        assertThat(objectMapper.valueToTree(requestCaptor.getValue()).has("max_candidates")).isFalse();
        assertThat(requestCaptor.getValue().getImageMaxWidth()).isEqualTo(448);
        assertThat(requestCaptor.getValue().getImageJpegQuality()).isEqualTo(12);
        assertThat(job.getStatus()).isEqualTo(SocialLocationJobStatus.PROCESSING);
    }

    @Test
    void processingCallbackPersistsIncrementalCandidatesAndReusesExistingDatabasePlace() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID existingPlaceId = UUID.randomUUID();
        SocialLocationJob job = processingJob(jobId, userId, "tiktok");
        when(jobMapper.findById(jobId)).thenReturn(job);
        when(placeImportJobMapper.findSocialItemsBySocialJobId(jobId)).thenReturn(List.of());
        when(placeMapper.findByPlaceId("google-place-1")).thenReturn(Place.builder()
                .id(existingPlaceId)
                .placeId("google-place-1")
                .title("Existing cafe")
                .thumbnail("https://cdn.example/place.jpg")
                .build());

        service.handleCallback(SocialLocationJobCallbackRequest.builder()
                .gorouteJobId(jobId)
                .status("PROCESSING")
                .result(objectMapper.readTree("""
                        {
                          "partial": true,
                          "progress": {"processedCandidates": 1, "totalCandidates": 4},
                          "extraction": {
                            "provider": "OPENAI",
                            "model": "gpt-5-mini",
                            "candidateValidation": {"policy": "AI_EVIDENCE_JUDGE_V2"},
                            "candidates": [{
                              "name": "Existing cafe",
                              "verification": {
                                "status": "VERIFIED",
                                "policy": "AI_EVIDENCE_JUDGE_V2"
                              },
                              "mapSearch": {"candidates": [{"placeId": "google-place-1"}]}
                            }]
                          }
                        }
                        """))
                .build());

        assertThat(job.getStatus()).isEqualTo(SocialLocationJobStatus.PROCESSING);
        assertThat(job.getCompletedAt()).isNull();
        assertThat(job.getResultPayload())
                .contains("\"processedCandidates\":1")
                .contains(existingPlaceId.toString())
                .contains("EXISTING_DATABASE")
                .contains("SKIPPED_EXISTING");
        verify(placeImportJobService, never()).createFromSocialJobs(any(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<AiApiCall> aiCallCaptor = ArgumentCaptor.forClass(AiApiCall.class);
        verify(aiApiCallMapper).upsert(aiCallCaptor.capture());
        assertThat(aiCallCaptor.getValue().getStatus()).isEqualTo("PROCESSING");
        assertThat(aiCallCaptor.getValue().getCandidateCount()).isEqualTo(1);
        assertThat(aiCallCaptor.getValue().getCompletedAt()).isNull();
    }

    private SocialLocationJob processingJob(UUID jobId, UUID userId, String platform) {
        return SocialLocationJob.builder()
                .id(jobId)
                .userId(userId)
                .sourceUrl("https://www." + platform + ".com/video/123")
                .platform(platform)
                .status(SocialLocationJobStatus.PROCESSING)
                .language("vi")
                .userTier("FREE")
                .build();
    }
}
