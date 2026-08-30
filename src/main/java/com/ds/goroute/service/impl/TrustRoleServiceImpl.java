package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.ApplyTrustRoleRequest;
import com.ds.goroute.dto.request.DecideTrustRoleRequest;
import com.ds.goroute.dto.response.TrustRoleResponse;
import com.ds.goroute.entity.Province;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserTrustRole;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserTrustRoleRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.TrustRoleService;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.TrustRole;
import com.ds.goroute.type.TrustRoleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustRoleServiceImpl implements TrustRoleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_REVIEW_MONTHS = 12;

    private final UserTrustRoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PassportMapper passportMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public TrustRoleResponse apply(UUID userId, ApplyTrustRoleRequest request) {
        // A role tied to an area is meaningless without one, so it is refused rather than
        // granted vaguely.
        if (request.getRole().requiresArea()
                && (request.getAreaProvinceCode() == null || request.getAreaProvinceCode().isBlank())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A local expert application must name a province");
        }
        if (request.getAreaProvinceCode() != null && !request.getAreaProvinceCode().isBlank()
                && passportMapper.findProvinceByCode(request.getAreaProvinceCode()) == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Province not found");
        }

        LocalDateTime now = LocalDateTime.now();
        UserTrustRole role = UserTrustRole.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .role(request.getRole())
                .areaProvinceCode(blankToNull(request.getAreaProvinceCode()))
                .status(TrustRoleStatus.PENDING)
                .applicationNote(request.getApplicationNote())
                .createdAt(now)
                .updatedAt(now)
                .build();
        if (roleRepository.insert(role) == 0) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "You already have an application or a decision for this role");
        }
        return toResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrustRoleResponse> mine(UUID userId) {
        return roleRepository.findByUser(userId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrustRoleResponse> approvedFor(UUID userId) {
        return roleRepository.findApprovedByUser(userId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrustRoleResponse> queue(TrustRoleStatus status, TrustRole role, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return roleRepository.findQueue(
                        status == null ? null : status.name(),
                        role == null ? null : role.name(),
                        safeSize, Math.max(0, page) * safeSize).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countQueue(TrustRoleStatus status, TrustRole role) {
        return roleRepository.countQueue(status == null ? null : status.name(),
                role == null ? null : role.name());
    }

    @Override
    @Transactional
    public TrustRoleResponse decide(UUID operatorId, UUID roleId, DecideTrustRoleRequest request) {
        UserTrustRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Application not found"));
        if (request.getStatus() == TrustRoleStatus.PENDING) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A decision must be APPROVED, REJECTED or REVOKED");
        }

        // Only an approval gets a review date. A rejection has nothing to expire.
        LocalDateTime reviewDueAt = request.getStatus() == TrustRoleStatus.APPROVED
                ? LocalDateTime.now().plusMonths(request.getReviewInMonths() == null
                        ? DEFAULT_REVIEW_MONTHS : request.getReviewInMonths())
                : null;

        roleRepository.decide(roleId, request.getStatus().name(), operatorId,
                request.getDecisionNote(), reviewDueAt);
        notifyApplicant(role, request.getStatus(), request.getDecisionNote());

        return roleRepository.findById(roleId).map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Application not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrustRoleResponse> dueForReview(int limit) {
        return roleRepository.findDueForReview(Math.max(1, Math.min(limit, MAX_PAGE_SIZE))).stream()
                .map(this::toResponse)
                .toList();
    }

    private void notifyApplicant(UserTrustRole role, TrustRoleStatus status, String note) {
        try {
            notificationService.createNotification(
                    role.getUserId(), null, NotificationType.ADMIN_MESSAGE,
                    switch (status) {
                        case APPROVED -> "Hồ sơ của bạn đã được duyệt";
                        case REJECTED -> "Hồ sơ của bạn chưa được duyệt";
                        case REVOKED -> "Huy hiệu của bạn đã bị thu hồi";
                        case PENDING -> "Hồ sơ của bạn đang được xem xét";
                    },
                    note,
                    Map.of("role", role.getRole().name(), "status", status.name()),
                    role.getDecidedBy());
        } catch (RuntimeException exception) {
            log.warn("Could not notify {} about their role decision: {}",
                    role.getUserId(), exception.getMessage(), exception);
        }
    }

    private TrustRoleResponse toResponse(UserTrustRole role) {
        User user = userRepository.findById(role.getUserId()).orElse(null);
        Province province = role.getAreaProvinceCode() == null
                ? null
                : passportMapper.findProvinceByCode(role.getAreaProvinceCode());

        return TrustRoleResponse.builder()
                .id(role.getId())
                .userId(role.getUserId())
                .userDisplayName(user == null ? null : user.getFullName())
                .role(role.getRole())
                .areaProvinceCode(role.getAreaProvinceCode())
                .areaProvinceName(province == null ? null : province.getName())
                .status(role.getStatus())
                .applicationNote(role.getApplicationNote())
                .decisionNote(role.getDecisionNote())
                .decidedAt(role.getDecidedAt())
                .grantedAt(role.getGrantedAt())
                .reviewDueAt(role.getReviewDueAt())
                .createdAt(role.getCreatedAt())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
