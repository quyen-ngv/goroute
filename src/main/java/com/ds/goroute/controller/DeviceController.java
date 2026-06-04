package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpdateDeviceRequest;
import com.ds.goroute.mapper.UserDeviceMapper;
import com.ds.goroute.service.notification.NotificationLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final UserDeviceMapper userDeviceMapper;

    @PatchMapping("/{deviceId}")
    public ResponseEntity<BaseResponse<Void>> updateDevice(
            @RequestAttribute("userId") UUID userId,
            @PathVariable UUID deviceId,
            @RequestBody UpdateDeviceRequest request) {
        userDeviceMapper.updateDevice(
                deviceId,
                userId,
                request.getFcmToken(),
                request.getLanguage() != null ? NotificationLanguage.normalize(request.getLanguage()) : null,
                request.getIsActive()
        );
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<BaseResponse<Void>> deleteDevice(
            @RequestAttribute("userId") UUID userId,
            @PathVariable UUID deviceId) {
        userDeviceMapper.deleteByIdAndUserId(deviceId, userId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }
}
