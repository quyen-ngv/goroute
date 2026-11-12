package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.HandOverManagedAccountRequest;
import com.ds.goroute.dto.request.ManagedAccountRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.ManagedAccountResponse;
import com.ds.goroute.dto.response.ManagedCheckinMediaResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.type.ManagedAccountStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Accounts the team holds and posts for, then hands to their owner (epic 20,
 * .docs/BLOG_POSTS_PLAN_2026-11-10.md §6). Acting for an account goes through the same user use
 * cases the app calls, so every rule of a check-in applies to it unchanged.
 */
public interface ManagedAccountService {

    SlicePageResponse<ManagedAccountResponse> list(String search, ManagedAccountStatus status, int page, int size);

    ManagedAccountResponse get(UUID userId);

    ManagedAccountResponse create(UUID adminId, ManagedAccountRequest request);

    ManagedAccountResponse update(UUID adminId, UUID userId, ManagedAccountRequest request);

    ManagedAccountResponse handOver(UUID adminId, UUID userId, HandOverManagedAccountRequest request);

    UserCheckinResponse createCheckin(UUID userId, CreateUserCheckinRequest request);

    UserCheckinResponse updateCheckin(UUID userId, UUID checkinId, UpdateUserCheckinRequest request);

    void deleteCheckin(UUID userId, UUID checkinId);

    ManagedCheckinMediaResponse uploadCheckinMedia(UUID userId, MultipartFile file);
}
