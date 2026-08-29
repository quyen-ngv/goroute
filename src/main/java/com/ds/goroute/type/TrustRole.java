package com.ds.goroute.type;

/**
 * Community roles granted by review (TRUST-02).
 *
 * <p>Start with CREATOR only in operation: four roles is more than a small team can judge
 * consistently and more than users can tell apart. The others exist so the data model does
 * not need changing when the programme grows.
 */
public enum TrustRole {
    CREATOR,
    KOL,
    KOC,
    LOCAL_EXPERT;

    /** A role tied to a specific area; granting it without one would say nothing. */
    public boolean requiresArea() {
        return this == LOCAL_EXPERT;
    }
}
