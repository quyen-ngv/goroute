package com.ds.goroute.service.impl;

import com.ds.goroute.entity.SocialLocationSubmissionEvent;
import com.ds.goroute.entity.SocialLocationUserRestriction;
import com.ds.goroute.repository.SocialLocationRestrictionRepository;
import com.ds.goroute.service.SocialLocationAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialLocationAdminServiceImpl implements SocialLocationAdminService {
    private final SocialLocationRestrictionRepository repository;

    @Override
    public List<SocialLocationUserRestriction> listRestrictions(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return repository.findAdmin(normalized, safeSize, safePage * safeSize);
    }

    @Override
    public List<SocialLocationSubmissionEvent> listUserEvents(UUID userId, int limit) {
        return repository.findEventsByUserId(userId, Math.min(Math.max(limit, 1), 500));
    }

    @Override public void resetRestriction(UUID userId) { repository.reset(userId); }
}
