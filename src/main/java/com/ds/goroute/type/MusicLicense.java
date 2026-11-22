package com.ds.goroute.type;

/**
 * The licence a background-music track is offered under. Only licences that allow
 * commercial use and syncing music to pictures belong here (V282): not NC, ND or SA.
 */
public enum MusicLicense {
    /** Public domain dedication; no credit needed. */
    CC0("https://creativecommons.org/publicdomain/zero/1.0/"),
    /** Creative Commons Attribution 4.0; the credit line must be shown with the post. */
    CC_BY_4("https://creativecommons.org/licenses/by/4.0/");

    private final String url;

    MusicLicense(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
