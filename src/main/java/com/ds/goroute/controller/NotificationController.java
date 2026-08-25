package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AdminPushNotificationRequest;
import com.ds.goroute.dto.request.AdminSinglePushNotificationRequest;
import com.ds.goroute.dto.request.RegisterDeviceRequest;
import com.ds.goroute.dto.request.UpdateDeviceRequest;
import com.ds.goroute.dto.response.AdminPushNotificationResponse;
import com.ds.goroute.dto.response.NotificationResponse;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.dto.response.UserDeviceResponse;
import com.ds.goroute.service.UserDeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final UserDeviceService userDeviceService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getNotifications(
            @RequestAttribute("userId") UUID userId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "false") Boolean unreadOnly,
            @RequestParam(required = false) UUID tripId) {
        log.info("getNotifications - userId: {}, tripId: {}, page: {}, size: {}, unreadOnly: {}",
                userId, tripId, page, size, unreadOnly);
        List<NotificationResponse> notifications = notificationService.getNotifications(userId, page, size, unreadOnly, tripId);
        log.info("getNotifications - found {} notifications", notifications.size());
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
            @RequestAttribute("userId") UUID userId,
            @PathVariable UUID notificationId) {
        notificationService.markAsRead(userId, notificationId);
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
            @RequestAttribute("userId") UUID userId,
            @PathVariable UUID notificationId) {
        notificationService.deleteNotification(userId, notificationId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PostMapping("/admin/push")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<AdminPushNotificationResponse>> sendAdminPush(
            @RequestAttribute("userId") UUID adminUserId,
            @Valid @RequestBody AdminPushNotificationRequest request) {
        
        log.info("Admin push notification request from userId: {}, recipients: {}", 
                adminUserId, request.getEmails().size());
        
        var response = notificationService.sendAdminPushNotification(
                request.getEmails(),
                request.getTitle(),
                request.getBody(),
                request.getDeepLink(),
                request.getData(),
                request.getImageUrl(),
                request.getPriority()
        );

        log.info("Admin push completed: success={}, notFound={}, noDevice={}, failed={}",
                response.getSuccessCount(), 
                response.getNotFoundCount(), 
                response.getNoDeviceCount(),
                response.getFailedCount());

        return ResponseEntity.ok(BaseResponse.ofSucceeded(response));
    }

    @PostMapping("/admin/push/user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<AdminPushNotificationResponse>> sendAdminPushToUser(
            @RequestAttribute("userId") UUID adminUserId,
            @Valid @RequestBody AdminSinglePushNotificationRequest request) {

        log.info("Single admin push notification request from userId: {}", adminUserId);

        var response = notificationService.sendAdminPushNotificationToUser(
                request.getUserId(),
                request.getEmail(),
                request.getTitle(),
                request.getBody(),
                request.getDeepLink(),
                request.getData(),
                request.getImageUrl(),
                request.getPriority()
        );

        log.info("Single admin push completed: success={}, notFound={}, noDevice={}, failed={}",
                response.getSuccessCount(),
                response.getNotFoundCount(),
                response.getNoDeviceCount(),
                response.getFailedCount());

        return ResponseEntity.ok(BaseResponse.ofSucceeded(response));
    }

    @PostMapping("/devices")
    public ResponseEntity<BaseResponse<UserDeviceResponse>> registerDevice(
            @RequestAttribute("userId") UUID userId,
            @Valid @RequestBody RegisterDeviceRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(userDeviceService.register(userId, request)));
    }

    @PatchMapping("/devices/{deviceId}")
    public ResponseEntity<BaseResponse<Void>> updateDevice(
            @RequestAttribute("userId") UUID userId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateDeviceRequest request) {
        userDeviceService.update(userId, deviceId, request);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }
}
