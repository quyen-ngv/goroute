package com.ds.goroute.dto.response;

import com.ds.goroute.type.CheckinPhotoSource;
import lombok.Builder;
import lombok.Data;

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
}
