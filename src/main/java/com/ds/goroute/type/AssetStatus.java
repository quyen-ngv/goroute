package com.ds.goroute.type;

/**
 * An archived asset is hidden from the console pickers but still resolves for the records that
 * already use it, so archiving never breaks a page.
 */
public enum AssetStatus {
    ACTIVE,
    ARCHIVED
}
