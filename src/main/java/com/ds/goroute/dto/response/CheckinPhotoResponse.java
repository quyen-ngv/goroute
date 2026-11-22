package com.ds.goroute.dto.response;

import com.ds.goroute.type.CheckinMediaType;
import com.ds.goroute.type.CheckinPhotoSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CheckinPhotoResponse {
    private UUID id;
    private String url;
    private CheckinPhotoSource source;
    private Integer position;

    /** Words about this one photo. Null when the author wrote none. */
    private String title;
    private String description;

    private LocalDateTime capturedAt;

    /**
     * Where the shutter fired, as the file reported it.
     *
     * <p>Sent back so an edit can return it unchanged. Editing a check-in replaces its
     * whole photo list, so anything the client cannot see is anything the client cannot
     * preserve: without these, correcting a caption would quietly erase the evidence
     * that put the photo at the place.
     */
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyMeters;

    /** IMAGE or VIDEO. A VIDEO is the only item of its check-in; {@code url} is the video file. */
    private CheckinMediaType mediaType;
    private String thumbnailUrl;
    private Integer durationSeconds;

    /** The camera filter and frame the photo was taken with; null when none or unknown. */
    private String filterKey;
    private String frameKey;
}
