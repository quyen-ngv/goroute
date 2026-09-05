package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.entity.AiTripGenerationJob;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.service.AiTripGenerationService;
import com.ds.goroute.service.AiTripWorkerDispatcher;
import com.ds.goroute.service.SocialItineraryRequestFactory;
import com.ds.goroute.service.SocialLocationCompletionService;
import com.ds.goroute.service.SocialSavedSpotService;
import com.ds.goroute.type.SocialLocationOperation;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Keeps the social extraction job as the durable source of truth while projecting its result into
 * saved spots and, for the default itinerary flow, the normal AI-trip pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialLocationCompletionServiceImpl implements SocialLocationCompletionService {
    private final SocialSavedSpotService socialSavedSpotService;
    private final SocialItineraryRequestFactory itineraryRequestFactory;
    private final AiTripGenerationService aiTripGenerationService;
    private final AiTripWorkerDispatcher aiTripWorkerDispatcher;
    private final SocialLocationJobMapper jobMapper;

    @Override
    @Transactional
    public boolean handleCompleted(SocialLocationJob job, JsonNode result) {
        if (job == null || result == null || result.isNull()) return false;

        int saved = socialSavedSpotService.saveFromJob(job.getUserId(), job.getId(), result);
        job.setSavedSpotCount(saved);

        SocialLocationOperation operation = job.getOperation() == null
                ? SocialLocationOperation.GEN_ITINERARY : job.getOperation();
        AiTripGenerateRequest request = operation == SocialLocationOperation.GEN_ITINERARY
                ? itineraryRequestFactory.build(job, result) : null;
        if (request == null) {
            jobMapper.update(job);
            return false;
        }

        if (job.getAiTripJobId() != null) {
            jobMapper.update(job);
            return true;
        }

        try {
            AiTripGenerationJob aiJob = aiTripGenerationService.create(
                    request,
                    job.getUserId(),
                    "social:" + job.getId(),
                    job.getLanguage());
            job.setAiTripJobId(aiJob.getId());
            jobMapper.update(job);
            dispatchAfterCommit(aiJob);
            return true;
        } catch (RuntimeException exception) {
            // Extraction and saved spots remain useful when the separate AI-trip quota is
            // exhausted or the trip request is malformed. The user can retry itinerary creation
            // from the cached video without paying for extraction again.
            log.warn("Could not start AI itinerary for social job {}: {}", job.getId(), exception.getMessage());
            jobMapper.update(job);
            return false;
        }
    }

    private void dispatchAfterCommit(AiTripGenerationJob aiJob) {
        Runnable dispatch = () -> aiTripWorkerDispatcher.dispatch(aiJob);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch.run();
            }
        });
    }
}
