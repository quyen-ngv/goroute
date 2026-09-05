package com.ds.goroute.service;

import com.ds.goroute.dto.response.SocialSavedSpotResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public interface SocialSavedSpotService {
    int saveFromJob(UUID userId, UUID socialJobId, JsonNode result);

    List<SocialSavedSpotResponse> listMine(UUID userId, int page, int size);

    void delete(UUID userId, UUID spotId);
}
