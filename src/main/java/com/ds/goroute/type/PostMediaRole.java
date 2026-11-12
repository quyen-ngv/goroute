package com.ds.goroute.type;

/** media_assets.asset_role of a post's media (entity_type = 'POST'). */
public enum PostMediaRole {
    /** Uploaded for the cover. The body may still show it. */
    COVER,
    /** Uploaded to be shown inside the body as ![caption](media:id). */
    BODY
}
