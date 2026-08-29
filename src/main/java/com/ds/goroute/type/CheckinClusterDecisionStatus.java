package com.ds.goroute.type;

/** What an operator decided about a cluster of check-ins at one spot (CHK-12). */
public enum CheckinClusterDecisionStatus {
    /** Became a new row in the catalogue. */
    PROMOTED,
    /** Not worth cataloguing. Comes back if the cluster grows substantially. */
    IGNORED,
    /** Recognised as somewhere already in the catalogue under a different name. */
    MERGED
}
