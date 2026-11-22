package com.ds.goroute.type;

public enum PlaceImportSourceType {
    SOCIAL_LOCATION,
    ACTIVITY,
    MANUAL,
    ADMIN_LINKS,
    /** An AI agent's proposed place for an unlinked activity; a human approves the link. */
    AGENT_PROPOSAL,
    NATIONWIDE,
    PLACE_DETAILS_REFRESH
}
