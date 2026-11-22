package com.ds.goroute.service;

import com.ds.goroute.dto.response.AiTripUsage;

import java.util.UUID;

public interface AiTripService {
    AiTripUsage getEligibility(UUID userId);
}
