package com.ds.goroute.type;

/** Filter of the console's managed-account list (epic 20). */
public enum ManagedAccountStatus {
    /** users.managed_by_admin is true: the console may still act for the account. */
    MANAGED,
    /** Handed over to its owner (handed_over_at set); read-only in the console. */
    HANDED_OVER,
    /** Both of the above. */
    ALL
}
