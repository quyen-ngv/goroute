package com.ds.goroute.type;

/** What a place edit suggestion is about (epic 09). ADD_PHOTOS is phase 2 and refused for now. */
public enum PlaceEditType {
    /** Structured field changes and/or a free description of what is wrong. */
    EDIT_INFO,
    /** Closed, gone, a duplicate, moved or reopened; only BUSINESS_STATUS, a description and photos. */
    STATUS_REPORT,
    /** Phase 2: photos of the place. */
    ADD_PHOTOS
}
