package com.ds.goroute.service;

import com.ds.goroute.entity.SocialLocationJob;
import com.fasterxml.jackson.databind.JsonNode;

/** Handles durable side effects after a social extraction reaches COMPLETED. */
public interface SocialLocationCompletionService {
    /**
     * Saves every extracted candidate and starts an AI-trip job when this job is an itinerary.
     *
     * @return true when an itinerary AI job was created or was already attached to the social job
     */
    boolean handleCompleted(SocialLocationJob job, JsonNode result);
}
