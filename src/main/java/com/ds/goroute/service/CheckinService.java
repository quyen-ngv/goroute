package com.ds.goroute.service;

import com.ds.goroute.dto.request.CheckinRequest;
import com.ds.goroute.dto.response.CheckinResponse;

import java.util.List;
import java.util.UUID;

public interface CheckinService {
    CheckinResponse checkin(UUID tripId, UUID activityId, CheckinRequest request, UUID userId);
    
    List<CheckinResponse> getCheckins(UUID tripId, UUID activityId);
}
