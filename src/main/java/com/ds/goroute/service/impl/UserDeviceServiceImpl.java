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

    /**
     * Registers this phone's token for this person.
     *
     * <p>A token is one app install, so it has exactly one owner: whoever registered it last.
     * Registering takes the row over from any other account that signed in on the same phone
     * before, which otherwise kept receiving this person's pushes (and they theirs).
     */
    @Override
    @Transactional
    public UserDeviceResponse register(UUID userId, RegisterDeviceRequest request) {
        LocalDateTime now = LocalDateTime.now();
        UserDevice candidate = UserDevice.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .fcmToken(request.getFcmToken())
                .deviceType(request.getDeviceType())
                .deviceName(request.getDeviceName())
                .language(NotificationLanguage.normalize(request.getLanguage()))
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        UserDevice saved = userDeviceMapper.upsertByToken(candidate);
        return toResponse(saved == null ? candidate : saved);
    }

    @Override
    @Transactional
    public void update(UUID userId, UUID deviceId, UpdateDeviceRequest request) {
        String language = request.getLanguage() == null
                ? null
                : NotificationLanguage.normalize(request.getLanguage());
        if (request.getFcmToken() != null) {
            // Same single-owner rule as register; rolled back with the update when the device is not theirs.
            userDeviceMapper.deleteByTokenExceptDevice(request.getFcmToken(), deviceId);
        }
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

    @Override
    @Transactional
    public void deleteAllForUser(UUID userId) {
        userDeviceMapper.deleteByUserId(userId);
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
