package com.ds.goroute.dto.response;

import com.ds.goroute.type.CheckinMediaType;
import lombok.Builder;
import lombok.Value;

/**
 * A file the console uploaded for a managed account's check-in. The console then sends it in a
 * CreateUserCheckinRequest photo item ({@code mediaType} VIDEO with a poster image as thumbnailUrl).
 */
@Value
@Builder
public class ManagedCheckinMediaResponse {
    String url;
    CheckinMediaType mediaType;
    /** Read from an MP4/MOV header; null for an image or a WEBM. */
    Integer durationSeconds;
}
