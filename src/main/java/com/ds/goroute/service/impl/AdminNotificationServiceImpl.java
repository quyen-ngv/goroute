package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AdminBroadcastNotificationRequest;
import com.ds.goroute.dto.request.AdminNotificationAudienceRequest;
import com.ds.goroute.dto.response.AdminNotificationAudienceResponse;
import com.ds.goroute.dto.response.AdminNotificationHistoryResponse;
import com.ds.goroute.dto.response.AdminNotificationRecipientResponse;
import com.ds.goroute.dto.response.AdminPushNotificationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminNotificationMapper;
import com.ds.goroute.service.AdminNotificationService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.type.AdminNotificationAudience;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationServiceImpl implements AdminNotificationService {

    /** A send larger than this is refused rather than run: narrow the segment instead. */
    private static final int MAX_RECIPIENTS_PER_SEND = 5000;
    private static final int DEFAULT_WITHIN_DAYS = 7;
    private static final int MAX_SAMPLE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminNotificationMapper adminNotificationMapper;
    private final NotificationService notificationService;

    @Override
    public AdminNotificationAudienceResponse previewAudience(AdminNotificationAudienceRequest request, int sampleSize) {
        AdminNotificationAudience audience = requireAudience(request);
        Integer withinDays = resolveWithinDays(audience, request.getWithinDays());
        List<UUID> userIds = normalizedUserIds(audience, request.getUserIds());
        int safeSample = Math.min(Math.max(sampleSize, 1), MAX_SAMPLE_SIZE);

        /* The preview always reports both numbers: the operator needs to see how much of the
         * segment is unreachable by push before deciding to send. */
        long totalCount = adminNotificationMapper.countAudience(audience.name(), userIds, withinDays, false);
        long deliverableCount = adminNotificationMapper.countAudience(audience.name(), userIds, withinDays, true);

        boolean onlyWithDevice = Boolean.TRUE.equals(request.getOnlyWithDevice());
        List<AdminNotificationRecipientResponse> sample = adminNotificationMapper.findAudienceRecipients(
                audience.name(), userIds, withinDays, onlyWithDevice, safeSample, 0);

        return AdminNotificationAudienceResponse.builder()
                .totalCount(totalCount)
                .deliverableCount(deliverableCount)
                .sample(sample)
                .build();
    }

    @Override
    public AdminPushNotificationResponse broadcast(AdminBroadcastNotificationRequest request) {
        AdminNotificationAudienceRequest audienceRequest = request.getAudience();
        AdminNotificationAudience audience = requireAudience(audienceRequest);
        Integer withinDays = resolveWithinDays(audience, audienceRequest.getWithinDays());
        List<UUID> userIds = normalizedUserIds(audience, audienceRequest.getUserIds());
        /* A send defaults to device holders only: a row nobody can be told about is not a send. */
        boolean onlyWithDevice = !Boolean.FALSE.equals(audienceRequest.getOnlyWithDevice());

        long total = adminNotificationMapper.countAudience(audience.name(), userIds, withinDays, onlyWithDevice);
        if (total == 0) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Không có người dùng nào khớp với nhóm đã chọn");
        }
        if (total > MAX_RECIPIENTS_PER_SEND) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, String.format(
                    "Nhóm hiện có %d người, vượt giới hạn %d cho một lần gửi. Hãy thu hẹp nhóm.",
                    total, MAX_RECIPIENTS_PER_SEND));
        }

        List<UUID> recipients = adminNotificationMapper.findAudienceUserIds(
                audience.name(), userIds, withinDays, onlyWithDevice, MAX_RECIPIENTS_PER_SEND);

        log.info("Admin announcement '{}' to audience {} resolved to {} recipients",
                request.getTitle(), audience, recipients.size());

        return notificationService.sendAdminPushNotificationToUsers(
                recipients,
                request.getTitle(),
                request.getBody(),
                blankToNull(request.getDeepLink()),
                request.getData(),
                blankToNull(request.getImageUrl()),
                request.getPriority());
    }

    @Override
    public PageResponse<AdminNotificationRecipientResponse> searchRecipients(String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String keyword = blankToNull(search);
        return PageResponse.of(
                adminNotificationMapper.searchRecipients(keyword, safeSize, safePage * safeSize),
                adminNotificationMapper.countSearchRecipients(keyword),
                safePage,
                safeSize);
    }

    @Override
    public PageResponse<AdminNotificationHistoryResponse> history(String search, String sort, boolean descending, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String keyword = blankToNull(search);
        return PageResponse.of(
                adminNotificationMapper.findHistory(keyword, sort, descending, safeSize, safePage * safeSize),
                adminNotificationMapper.countHistory(keyword),
                safePage,
                safeSize);
    }

    private AdminNotificationAudience requireAudience(AdminNotificationAudienceRequest request) {
        if (request == null || request.getAudience() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Audience is required");
        }
        return request.getAudience();
    }

    private Integer resolveWithinDays(AdminNotificationAudience audience, Integer requested) {
        if (!audience.requiresWithinDays()) return null;
        return requested == null ? DEFAULT_WITHIN_DAYS : requested;
    }

    private List<UUID> normalizedUserIds(AdminNotificationAudience audience, List<UUID> userIds) {
        if (audience != AdminNotificationAudience.SPECIFIC_USERS) return null;
        List<UUID> picked = userIds == null
                ? List.of()
                : userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (picked.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Vui lòng chọn ít nhất một người dùng");
        }
        return picked;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
