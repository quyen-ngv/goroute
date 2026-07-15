package com.ds.goroute.service;

import com.ds.goroute.dto.request.ApplyReferralCodeRequest;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.dto.response.ReferralStatusResponse;
import com.ds.goroute.mapper.ReferralMapper;
import com.ds.goroute.repository.UserRepository;
import static com.ds.goroute.constant.ErrorConstant.INVALID_PARAMETERS;
import static com.ds.goroute.constant.ErrorConstant.USER_NOT_FOUND;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferralService {
    private final ReferralMapper referralMapper;
    private final UserRepository userRepository;
    private final StarService starService;

    public ReferralStatusResponse getStatus(UUID inviteeId) {
        User invitee = userRepository.findById(inviteeId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND, "User not found"));
        boolean hasApplied = referralMapper.countByInviteeId(inviteeId) > 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = invitee.getCreatedAt() == null
                ? now
                : invitee.getCreatedAt().plusDays(3);
        boolean withinWindow = invitee.getCreatedAt() != null && now.isBefore(expiresAt);
        int daysRemaining = withinWindow
                ? Math.max(1, (int) Math.ceil(
                        Duration.between(now, expiresAt).toMinutes() / 1440.0))
                : 0;

        return ReferralStatusResponse.builder()
                .canApply(!hasApplied && withinWindow)
                .hasApplied(hasApplied)
                .daysRemaining(daysRemaining)
                .build();
    }

    @Transactional
    public void applyCode(UUID inviteeId, ApplyReferralCodeRequest request) {
        User invitee = userRepository.findById(inviteeId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND, "User not found"));
        if (invitee.getCreatedAt() == null || invitee.getCreatedAt().isBefore(LocalDateTime.now().minusDays(3))) {
            throw new BusinessException(INVALID_PARAMETERS, "Invite codes are available only within 3 days of registration");
        }
        if (referralMapper.countByInviteeId(inviteeId) > 0) {
            throw new BusinessException(INVALID_PARAMETERS, "You have already used an invite code");
        }

        String code = request.getCode().trim();
        if (code.regionMatches(true, 0, "INV-", 0, 4)) code = code.substring(4);
        User inviter = userRepository.findByUsername(code)
                .orElseThrow(() -> new BusinessException(INVALID_PARAMETERS, "Invite code is invalid"));
        if (inviter.getId().equals(inviteeId)) {
            throw new BusinessException(INVALID_PARAMETERS, "You cannot use your own invite code");
        }

        referralMapper.insert(UUID.randomUUID(), inviter.getId(), inviteeId, "INV-" + code);
        starService.grant(inviter.getId(), 1, "INVITE_SENT", "referral:inviter:" + inviteeId,
                "A new traveler joined with your invite code");
        starService.grant(inviteeId, 1, "INVITE_ACCEPTED", "referral:invitee:" + inviteeId,
                "You joined with an invite code");
    }
}
