package com.ds.goroute.service;

import com.ds.goroute.entity.SocialLocationSubmissionEvent;
import com.ds.goroute.entity.SocialLocationUserRestriction;

import java.util.List;
import java.util.UUID;

public interface SocialLocationAdminService {
    List<SocialLocationUserRestriction> listRestrictions(String status, int page, int size);
    List<SocialLocationSubmissionEvent> listUserEvents(UUID userId, int limit);
    void resetRestriction(UUID userId);
}
