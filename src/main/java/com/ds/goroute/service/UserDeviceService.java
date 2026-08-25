package com.ds.goroute.service;

import com.ds.goroute.dto.request.RegisterDeviceRequest;
import com.ds.goroute.dto.request.UpdateDeviceRequest;
import com.ds.goroute.dto.response.UserDeviceResponse;

import java.util.UUID;

public interface UserDeviceService {
    UserDeviceResponse register(UUID userId, RegisterDeviceRequest request);

    void update(UUID userId, UUID deviceId, UpdateDeviceRequest request);

    void delete(UUID userId, UUID deviceId);
}
