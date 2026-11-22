package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One account of the console's managed-account list: the user columns plus the guide grant and
 * three counts, read in one query (UserMapper.selectManagedAccounts).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedAccountRow {
    private UUID id;
    private String fullName;
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private Boolean managedByAdmin;
    private LocalDateTime managedSince;
    private LocalDateTime handedOverAt;
    /** True when the account holds an ACTIVE user_guide_grants row. */
    private Boolean guide;
    private String guideTitle;
    private Integer postCount;
    private Integer checkinCount;
    private Integer followerCount;
}
