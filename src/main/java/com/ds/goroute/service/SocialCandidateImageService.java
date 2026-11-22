package com.ds.goroute.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Pictures of the places a social video names, cut by the worker from the frame the AI used as
 * evidence. The worker sends each one inline, once, in the first progress report; this service
 * stores it and keeps its URL on the candidate through every later report.
 */
public interface SocialCandidateImageService {

    /**
     * Uploads every inline {@code previewImage} under {@code extraction.candidates[]} and replaces
     * it with {@code imageUrl}. A picture that cannot be decoded or stored is dropped, never the
     * candidate. With no job id the pictures are only dropped.
     */
    JsonNode storePreviewImages(UUID socialJobId, JsonNode result);

    /**
     * Copies {@code imageUrl} from the result already stored for the job onto the candidate with
     * the same {@code candidateRef} in the incoming result, when the incoming one has none. Later
     * reports from the worker do not repeat the pictures.
     */
    JsonNode carryOverImageUrls(JsonNode storedResult, JsonNode incomingResult);
}
