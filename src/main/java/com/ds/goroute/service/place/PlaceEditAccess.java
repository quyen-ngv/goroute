package com.ds.goroute.service.place;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.PlaceEditGuard;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceEditUnavailableReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Who may suggest place edits, checked on the server (plan §3.6). The flag passes beta users
 * while it is off, the standing rule for every flagged feature.
 */
@Component
@RequiredArgsConstructor
public class PlaceEditAccess {

    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final UserRepository users;

    public boolean isEnabled(UUID userId) {
        return config.getBoolean(BusinessConfigKey.PLACE_EDIT_ENABLED) || betaAccess.isBetaUser(userId);
    }

    /** 403 PLACE_EDIT_DISABLED unless the flag is on or the caller is a beta user. */
    public void requireEnabled(UUID userId) {
        if (!isEnabled(userId)) {
            throw new BusinessException(ErrorConstant.PLACE_EDIT_DISABLED);
        }
    }

    /**
     * Why the caller may not send an EDIT_INFO suggestion for the place; null when they may.
     * PARTNER_PLACE still lets a closure report through.
     */
    public PlaceEditUnavailableReason unavailableReason(UUID userId, PlaceEditGuard place, LocalDateTime now) {
        if (!isEnabled(userId)) {
            return PlaceEditUnavailableReason.DISABLED;
        }
        if (!isVisible(place)) {
            return PlaceEditUnavailableReason.PLACE_INACTIVE;
        }
        if (isAccountTooNew(userId, now)) {
            return PlaceEditUnavailableReason.ACCOUNT_TOO_NEW;
        }
        if (place.isPartnerPlace()) {
            return PlaceEditUnavailableReason.PARTNER_PLACE;
        }
        return null;
    }

    public static boolean isVisible(PlaceEditGuard place) {
        return "ACTIVE".equals(place.getVisibilityStatus())
                && (place.getLifecycleStatus() == null || "ACTIVE".equals(place.getLifecycleStatus()));
    }

    public boolean isAccountTooNew(UUID userId, LocalDateTime now) {
        int hours = config.getInt(BusinessConfigKey.PLACE_EDIT_MIN_ACCOUNT_AGE_HOURS);
        if (hours <= 0) {
            return false;
        }
        LocalDateTime createdAt = users.findById(userId).map(User::getCreatedAt).orElse(null);
        return createdAt == null || createdAt.plusHours(hours).isAfter(now);
    }
}
