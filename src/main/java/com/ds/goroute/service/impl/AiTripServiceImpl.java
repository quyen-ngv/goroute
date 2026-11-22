package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.AiTripUsage;
import com.ds.goroute.service.AiTripQuotaService;
import com.ds.goroute.service.AiTripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiTripServiceImpl implements AiTripService {

    private final AiTripQuotaService aiTripQuotaService;

    @Override
    @Transactional
    public AiTripUsage getEligibility(UUID userId) {
        return aiTripQuotaService.getUsage(userId);
    }
}
