package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.RegisterDeviceRequest;
import com.ds.goroute.dto.request.UpdateDeviceRequest;
import com.ds.goroute.dto.response.UserDeviceResponse;
import com.ds.goroute.entity.UserDevice;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.UserDeviceMapper;
import com.ds.goroute.service.UserDeviceService;
import com.ds.goroute.service.notification.NotificationLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDeviceServiceImpl implements UserDeviceService {

    private final UserDeviceMapper userDeviceMapper;

    @Override
    @Transactional
    public UserDeviceResponse register(UUID userId, RegisterDeviceRequest request) {
        String language = NotificationLanguage.normalize(request.getLanguage());
        UserDevice device = userDeviceMapper.findByUserIdAndToken(userId, request.getFcmToken());
        if (device != null) {
            userDeviceMapper.updateDevice(
                    device.getId(), userId, request.getFcmToken(), language, true);
            device.setLanguage(language);
            device.setIsActive(true);
            device.setUpdatedAt(LocalDateTime.now());
            return toResponse(device);
        }

        LocalDateTime now = LocalDateTime.now();
        device = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .fcmToken(request.getFcmToken())
                .deviceType(request.getDeviceType())
                .deviceName(request.getDeviceName())
                .language(language)
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userDeviceMapper.insert(device);
        return toResponse(device);
    }

    @Override
    @Transactional
    public void update(UUID userId, UUID deviceId, UpdateDeviceRequest request) {
        String language = request.getLanguage() == null
                ? null
                : NotificationLanguage.normalize(request.getLanguage());
        int updated = userDeviceMapper.updateDevice(
                deviceId, userId, request.getFcmToken(), language, request.getIsActive());
        if (updated != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Device not found");
        }
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID deviceId) {
        if (userDeviceMapper.deleteByIdAndUserId(deviceId, userId) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Device not found");
        }
    }

    private UserDeviceResponse toResponse(UserDevice device) {
        return UserDeviceResponse.builder()
                .id(device.getId())
                .deviceType(device.getDeviceType())
                .deviceName(device.getDeviceName())
                .language(device.getLanguage())
                .isActive(device.getIsActive())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }
}
