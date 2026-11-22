package com.ds.goroute.type;

/**
 * PENDING until a member who may edit the trip applies or dismisses it. FAILED when applying was
 * refused by the trip itself (the activity was deleted meanwhile, the day no longer exists);
 * the plan is never half-changed.
 */
public enum AssistantProposalStatus {
    PENDING,
    APPLIED,
    DISMISSED,
    FAILED,
    STALE
}
