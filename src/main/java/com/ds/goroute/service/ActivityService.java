package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.ReorderActivitiesRequest;
import com.ds.goroute.dto.request.UpdateActivityRequest;
import com.ds.goroute.dto.response.ActivityResponse;

import java.util.List;
import java.util.UUID;

public interface ActivityService {
    ActivityResponse createActivity(UUID tripId, CreateActivityRequest request, UUID userId);
    
    List<ActivityResponse> getActivities(UUID tripId, Integer dayNumber);
    
    ActivityResponse updateActivity(UUID tripId, UUID activityId, UpdateActivityRequest request, UUID userId);
    
    void deleteActivity(UUID tripId, UUID activityId, UUID userId);
    
    void reorderActivities(UUID tripId, ReorderActivitiesRequest request, UUID userId);
}
