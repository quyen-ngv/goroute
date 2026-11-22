package com.ds.goroute.type;

/** State of a derived stop. Phase 1 only produces {@link #VISITED}. */
public enum TrackVisitStatus {
    VISITED,
    SUGGESTED,
    DISMISSED,
    ADDED
}
