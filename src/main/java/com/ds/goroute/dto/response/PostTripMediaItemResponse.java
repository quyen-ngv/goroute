package com.ds.goroute.dto.response;

import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.PostMediaSource;
import com.ds.goroute.type.PostMediaType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One photo or video the author may put in their trip story: a photo of one of their own check-ins
 * on (or during) the trip, or a trip memory they uploaded. The editor's "from the trip" tray.
 */
@Value
@Builder
public class PostTripMediaItemResponse {
    PostMediaSource sourceType;
    /** The photo row for CHECKIN_PHOTO, the media asset for TRIP_MEMORY. */
    UUID sourceId;
    /** The check-in a CHECKIN_PHOTO belongs to; null for a memory. */
    UUID checkinId;
    PostMediaType mediaType;
    String url;
    /** A video's poster; null for a photo. */
    String thumbnailUrl;
    Integer durationSeconds;
    /** The check-in's caption, or the memory's caption. */
    String caption;
    LocalDateTime takenAt;
    /** The check-in's visibility; a PRIVATE one becomes public by going into a public post. */
    ContentVisibility visibility;
    /** The stop this belongs to: the one it was recorded on, or the nearest stop when it named none. */
    UUID activityId;
    /** True when {@code activityId} is a guess by distance, not what the author recorded. */
    boolean suggested;
    /** The media row already on the post for this file, when the story already uses it. */
    UUID attachedMediaId;
}
