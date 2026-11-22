package com.ds.goroute.type;

/**
 * Who can open a saved category besides its owner.
 *
 * <p>Separate from {@link ContentVisibility} because a followers-only level is planned for
 * saved categories alone; widening the shared enum would hand it to every other content type.
 */
public enum SavedCategoryVisibility {
    /** Only the owner. */
    PRIVATE,
    /** Anyone signed in, listed on the owner's profile. */
    PUBLIC;

    public boolean isPublic() {
        return this == PUBLIC;
    }
}
