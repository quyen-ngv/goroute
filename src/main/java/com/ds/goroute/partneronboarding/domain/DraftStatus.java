package com.ds.goroute.partneronboarding.domain;

/**
 * Where a draft is in its short life.
 *
 * <p>Only {@link #DRAFT} accepts writes. {@link #SUBMITTED} is terminal and keeps the id of
 * the listing it produced, so a client that retries submit is told what already exists
 * instead of creating a second listing. {@link #ABANDONED} is what "delete" means here: the
 * row stays because it is the only record of an attempt that did not finish.
 */
public enum DraftStatus {
    DRAFT,
    SUBMITTED,
    ABANDONED;

    public boolean isEditable() {
        return this == DRAFT;
    }
}
