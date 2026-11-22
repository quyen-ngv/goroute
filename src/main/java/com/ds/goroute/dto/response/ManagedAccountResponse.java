package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/** One managed (or handed-over) account in the console (epic 20). */
@Value
@Builder
public class ManagedAccountResponse {
    UUID id;
    String fullName;
    String username;
    /** The placeholder {username}@managed.ondetour.invalid until the handover sets the real one. */
    String email;
    String avatarUrl;
    String bio;
    boolean guide;
    String guideTitle;
    LocalDateTime managedSince;
    /** Null while the console still holds the account. */
    LocalDateTime handedOverAt;
    int postCount;
    int checkinCount;
    int followerCount;
}
