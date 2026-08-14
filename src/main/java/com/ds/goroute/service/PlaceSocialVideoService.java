package com.ds.goroute.service;

import com.ds.goroute.dto.response.PlaceSocialVideoResponse;
import com.ds.goroute.entity.SocialLocationJob;

import java.util.List;
import java.util.UUID;

public interface PlaceSocialVideoService {
    void syncSocialJob(SocialLocationJob job);

    List<PlaceSocialVideoResponse> findByPlaceId(UUID placeId);
}
