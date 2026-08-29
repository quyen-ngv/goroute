package com.ds.goroute.type;

public enum GuideServiceStatus {
    DRAFT,
    LISTED,
    PAUSED;

    public boolean isBookable() {
        return this == LISTED;
    }
}
