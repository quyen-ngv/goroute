package com.ds.goroute.enums;

/**
 * What a row of {@code user_consents} is about. The first three are required to use the app and
 * can only be given; the rest are optional, off until the person turns them on, and can be
 * withdrawn at any time.
 */
public enum ConsentPurpose {
    /** Accepted the Terms of Service at {@code document_version}. */
    TERMS,
    /** Read the Privacy Policy at {@code document_version}. */
    PRIVACY,
    /** Declared being at least 16 years old. */
    AGE_16,
    /** Usage analytics and crash reports (Firebase Analytics, Crashlytics) on the device. */
    ANALYTICS,
    /** Pengoo keeps notes about the person across 1:1 chats ({@code assistant_profiles}). */
    ASSISTANT_MEMORY,
    /** Search engines may index the person's public web profile ({@code /goroute/u/{username}}). */
    PROFILE_SEARCH_INDEXING;

    public boolean isRequired() {
        return this == TERMS || this == PRIVACY || this == AGE_16;
    }
}
