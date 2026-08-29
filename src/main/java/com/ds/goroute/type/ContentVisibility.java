package com.ds.goroute.type;

/** Who can see a piece of user content. Defaults to the safer of the two. */
public enum ContentVisibility {
    PUBLIC,
    PRIVATE;

    public boolean isPublic() {
        return this == PUBLIC;
    }
}
