package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateSocialLocationJobRequest;
import com.ds.goroute.dto.request.SocialLocationJobCallbackRequest;
import com.ds.goroute.dto.response.SocialLocationJobResponse;

import java.util.List;
import java.util.UUID;

public interface SocialLocationJobService {
    SocialLocationJobResponse create(UUID userId, CreateSocialLocationJobRequest request);

    SocialLocationJobResponse get(UUID userId, UUID jobId);

    List<SocialLocationJobResponse> listMine(UUID userId, int page, int size);

    SocialLocationJobResponse handleCallback(SocialLocationJobCallbackRequest request);
}
