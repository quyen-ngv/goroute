package com.ds.goroute.type;

/**
 * Every kind of user-generated content that can be filtered, flagged, reported or
 * taken down. Flags, reports and takedowns address content by (type, id) instead of a
 * foreign key so a new content kind never needs a new table.
 */
public enum ModeratedContentType {
    REVIEW,
    CHECKIN,
    CHECKIN_PHOTO,
    TRIP,
    ACTIVITY,
    ACTIVITY_COMMENT,
    /** A comment on a trip, check-in, review or public collection. */
    CONTENT_COMMENT,
    TRIP_NOTE,
    TRIP_MEMORY,
    EXPENSE,
    CITY_STORY,
    PLACE_COLLECTION,
    /** A place name or address typed by a user: saved places and place contributions. */
    USER_PLACE,
    /** Partner-published catalogue text: hotels, rooms, rates, activities, packages. */
    PARTNER_LISTING,
    USER_PROFILE,
    CHAT_MESSAGE,
    /** Quest-level text a creator writes: title, summary, description, safety notes. */
    QUEST,
    /** Per-checkpoint text: names, stories, question prompts, hints. */
    QUEST_CHECKPOINT
}
