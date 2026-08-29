package com.ds.goroute.service;

import com.ds.goroute.dto.request.ApplyTrustRoleRequest;
import com.ds.goroute.dto.request.DecideTrustRoleRequest;
import com.ds.goroute.dto.response.TrustRoleResponse;
import com.ds.goroute.type.TrustRole;
import com.ds.goroute.type.TrustRoleStatus;

import java.util.List;
import java.util.UUID;

/**
 * Community roles granted by review (TRUST-02).
 *
 * <p>Kept apart from the automatic profile rank on purpose. Rank follows from activity;
 * a role is somebody's judgement, expires, and is looked at again. Follower counts can be
 * bought, which is why no number is allowed to grant one of these.
 *
 * <p>A granted role is also not a seller verification. Checking somebody's identity and
 * ability to trade is a different process with different consequences, and letting an
 * activity badge imply it would mislead the people it matters most to.
 */
public interface TrustRoleService {

    TrustRoleResponse apply(UUID userId, ApplyTrustRoleRequest request);

    List<TrustRoleResponse> mine(UUID userId);

    /** Roles in force, for decorating a public profile. */
    List<TrustRoleResponse> approvedFor(UUID userId);

    List<TrustRoleResponse> queue(TrustRoleStatus status, TrustRole role, int page, int size);

    long countQueue(TrustRoleStatus status, TrustRole role);

    TrustRoleResponse decide(UUID operatorId, UUID roleId, DecideTrustRoleRequest request);

    /** Roles whose review date has passed. */
    List<TrustRoleResponse> dueForReview(int limit);
}
