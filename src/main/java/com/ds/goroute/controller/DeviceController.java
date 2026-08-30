package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpdateDeviceRequest;
import com.ds.goroute.service.UserDeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final UserDeviceService userDeviceService;

    @PatchMapping("/{deviceId}")
    public ResponseEntity<BaseResponse<Void>> updateDevice(
            @CurrentUser UUID userId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateDeviceRequest request) {
        userDeviceService.update(userId, deviceId, request);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<BaseResponse<Void>> deleteDevice(
            @CurrentUser UUID userId,
            @PathVariable UUID deviceId) {
        userDeviceService.delete(userId, deviceId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }
}
