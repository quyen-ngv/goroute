package com.ds.goroute.type;

/**
 * What a {@link MarketplaceMessageType#SHARE} message points at.
 *
 * <p>The message stores only the kind and the id; every reader's app loads the item itself
 * through its own detail endpoint, so the card is always current and the item's own
 * visibility rule decides, per reader, whether it opens.
 */
public enum ChatShareType {
    PLACE,
    /** A dish (đặc sản). Its detail is scored per city, so a share also carries {@code citySlug}. */
    FOOD,
    CHECKIN,
    TRIP,
    EVENT,
    MEETUP
}
