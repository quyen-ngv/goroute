package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

/** A check-in video now in storage, ready to be sent as the check-in's only media item. */
@Data
@Builder
public class CheckinVideoUploadResponse {
    private String url;

    /** Read from the file's MP4/MOV header; null when the header could not be read (e.g. WEBM). */
    private Integer durationSeconds;
}
