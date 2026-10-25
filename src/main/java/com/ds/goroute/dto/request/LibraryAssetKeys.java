package com.ds.goroute.dto.request;

/** The asset key format, shared by the request validators and mirrored by the {@code asset_library} CHECK. */
public final class LibraryAssetKeys {

    public static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private LibraryAssetKeys() {
    }
}
