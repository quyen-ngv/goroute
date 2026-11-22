package com.ds.goroute.entity;

import com.ds.goroute.type.AuthProvider;
import com.ds.goroute.type.LocationTracking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private UUID id;
    private String email;
    private String passwordHash;
    private String fullName;
    private String username;
    private String avatarUrl;
    private String bio;
    private String socialLinks;
    private AuthProvider provider;
    private String providerId;
    private String defaultCurrency;
    private String defaultTravelMode;
    private LocationTracking locationTracking;
    private Integer autoCheckinRadius;
    private Integer budgetAlertThreshold;
    private Boolean budgetAlertDaily;
    private String defaultTripVisibility;
    private String language;
    private String theme;
    private Boolean onboardingCompleted;
    private Boolean mustChangePassword;
    private LocalDateTime passwordChangedAt;
    private String accountStatus;
    private LocalDateTime lastLoginAt;
    /**
     * An account an admin holds and posts for (V276). Written only by the managed-account
     * statements of UserMapper, never by {@code updateById}, so a profile save cannot flip it.
     */
    private Boolean managedByAdmin;
    private LocalDateTime managedSince;
    private LocalDateTime handedOverAt;
    private UUID handedOverBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
