package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.RegisterDeviceRequest;
import com.ds.goroute.dto.response.NotificationResponse;
import com.ds.goroute.entity.UserDevice;
import com.ds.goroute.mapper.UserDeviceMapper;
import com.ds.goroute.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final UserDeviceMapper userDeviceMapper;

    @GetMapping
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getNotifications(
            @RequestAttribute("userId") UUID userId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "false") Boolean unreadOnly) {
        List<NotificationResponse> notifications = notificationService.getNotifications(userId, page, size, unreadOnly);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(notifications));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<BaseResponse<Integer>> getUnreadCount(
            @RequestAttribute("userId") UUID userId) {
        Integer count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(count));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<BaseResponse<Void>> markAsRead(
            @PathVariable UUID notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PutMapping("/read-all")
    public ResponseEntity<BaseResponse<Void>> markAllAsRead(
            @RequestAttribute("userId") UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<BaseResponse<Void>> deleteNotification(
            @PathVariable UUID notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PostMapping("/devices")
    public ResponseEntity<BaseResponse<Void>> registerDevice(
            @RequestAttribute("userId") UUID userId,
            @RequestBody RegisterDeviceRequest request) {
        
        // Check if device already exists
        UserDevice existing = userDeviceMapper.findByUserIdAndToken(userId, request.getFcmToken());
        if (existing != null) {
            // Update existing device
            userDeviceMapper.updateToken(existing.getId(), request.getFcmToken());
        } else {
            // Create new device
            UserDevice device = UserDevice.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .fcmToken(request.getFcmToken())
                    .deviceType(request.getDeviceType())
                    .deviceName(request.getDeviceName())
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userDeviceMapper.insert(device);
        }
        
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }
}
