package com.ds.goroute.type;

/**
 * Re-hosting state of a local event image. PENDING rows are fetched by LocalEventImageJob through
 * the SSRF-guarded download path; FAILED after three attempts (or a moderation rejection).
 */
public enum LocalEventImageStatus {
    PENDING,
    DONE,
    FAILED
}
