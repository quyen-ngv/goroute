package com.ds.goroute.service.impl;

import com.ds.goroute.service.SocialCandidateImageService;
import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialCandidateImageServiceImpl implements SocialCandidateImageService {

    static final String PREVIEW_IMAGE_FIELD = "previewImage";
    static final String IMAGE_URL_FIELD = "imageUrl";

    /** The worker renders at most 720px wide and skips anything over 250 KB; this is the ceiling. */
    private static final int MAX_IMAGE_BYTES = 300_000;
    private static final String CONTENT_TYPE = "image/jpeg";
    private static final String KEY_PREFIX = "social-location/";
    private static final int MAX_KEY_SEGMENT_LENGTH = 64;

    private final StorageService storageService;

    @Override
    public JsonNode storePreviewImages(UUID socialJobId, JsonNode result) {
        if (!hasPreviewImage(result)) {
            return result;
        }
        JsonNode copy = result.deepCopy();
        int position = 0;
        for (JsonNode candidate : copy.path("extraction").path("candidates")) {
            position++;
            if (!(candidate instanceof ObjectNode candidateNode)) {
                continue;
            }
            JsonNode preview = candidateNode.remove(PREVIEW_IMAGE_FIELD);
            if (preview == null || socialJobId == null) {
                continue;
            }
            String imageUrl = upload(socialJobId, candidateNode, position, preview);
            if (imageUrl != null) {
                candidateNode.put(IMAGE_URL_FIELD, imageUrl);
            }
        }
        return copy;
    }

    @Override
    public JsonNode carryOverImageUrls(JsonNode storedResult, JsonNode incomingResult) {
        Map<String, String> storedUrls = imageUrlsByCandidateRef(storedResult);
        if (storedUrls.isEmpty() || incomingResult == null
                || !incomingResult.path("extraction").path("candidates").isArray()) {
            return incomingResult;
        }
        JsonNode copy = incomingResult.deepCopy();
        for (JsonNode candidate : copy.path("extraction").path("candidates")) {
            if (!(candidate instanceof ObjectNode candidateNode)
                    || !candidateNode.path(IMAGE_URL_FIELD).asText("").isBlank()) {
                continue;
            }
            String storedUrl = storedUrls.get(candidateNode.path("candidateRef").asText(""));
            if (storedUrl != null) {
                candidateNode.put(IMAGE_URL_FIELD, storedUrl);
            }
        }
        return copy;
    }

    private String upload(UUID socialJobId, ObjectNode candidate, int position, JsonNode preview) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(preview.path("data").asText(""));
        } catch (IllegalArgumentException e) {
            log.warn("Dropping undecodable candidate picture: job_id={} position={}", socialJobId, position);
            return null;
        }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES || !isJpeg(bytes)) {
            log.warn("Dropping candidate picture that is empty, too large or not JPEG: job_id={} position={} bytes={}",
                    socialJobId, position, bytes.length);
            return null;
        }
        String key = KEY_PREFIX + socialJobId + "/" + keySegment(candidate.path("candidateRef").asText(""), position)
                + ".jpg";
        try {
            return storageService.uploadBytes(bytes, key, CONTENT_TYPE);
        } catch (RuntimeException e) {
            // The place is still worth showing without its picture; the app falls back to the cover.
            log.warn("Could not store candidate picture: job_id={} position={} error={}",
                    socialJobId, position, e.getMessage());
            return null;
        }
    }

    private static boolean hasPreviewImage(JsonNode result) {
        if (result == null) {
            return false;
        }
        for (JsonNode candidate : result.path("extraction").path("candidates")) {
            if (candidate.has(PREVIEW_IMAGE_FIELD)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> imageUrlsByCandidateRef(JsonNode result) {
        Map<String, String> urls = new HashMap<>();
        if (result == null) {
            return urls;
        }
        for (JsonNode candidate : result.path("extraction").path("candidates")) {
            String candidateRef = candidate.path("candidateRef").asText("");
            String imageUrl = candidate.path(IMAGE_URL_FIELD).asText("");
            if (!candidateRef.isBlank() && !imageUrl.isBlank()) {
                urls.put(candidateRef, imageUrl);
            }
        }
        return urls;
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    /** candidateRef comes from the AI, so only a safe subset of it may reach an object key. */
    private static String keySegment(String candidateRef, int position) {
        String cleaned = candidateRef.replaceAll("[^A-Za-z0-9_-]", "-");
        if (cleaned.replace("-", "").isEmpty()) {
            return "candidate-" + position;
        }
        return cleaned.length() > MAX_KEY_SEGMENT_LENGTH ? cleaned.substring(0, MAX_KEY_SEGMENT_LENGTH) : cleaned;
    }
}
