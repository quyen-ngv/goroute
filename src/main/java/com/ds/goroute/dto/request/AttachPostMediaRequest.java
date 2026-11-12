package com.ds.goroute.dto.request;

import com.ds.goroute.type.PostMediaSource;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * {@code POST /posts/{postId}/media/attach}: puts one item of the author's own trip material (a
 * check-in photo or a trip memory, as {@code GET /posts/{postId}/trip-media} lists them) on the post
 * as a shared media row. No file is copied.
 */
@Data
public class AttachPostMediaRequest {

    @NotNull
    private PostMediaSource sourceType;

    /** The photo row id for CHECKIN_PHOTO, the media asset id for TRIP_MEMORY. */
    @NotNull
    private UUID sourceId;

    /** The check-in the photo belongs to; required for CHECKIN_PHOTO. */
    private UUID checkinId;
}
