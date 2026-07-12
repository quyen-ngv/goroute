package com.ds.goroute.type;

/**
 * Approval is deliberately separate from scrape status. A place can be
 * imported successfully but must not be linked to user-owned data yet.
 */
public enum PlaceImportApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
