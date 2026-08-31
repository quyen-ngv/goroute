package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Edits the words on a memory. The image itself is never re-pointed here: the
 * url feeds the legacy {@code memoryImageUrls} payload, which a repair migration
 * (V128) already had to clean up once.
 */
@Data
public class UpdateTripMemoryRequest {
    @Size(max = 120)
    @ModeratedText(contentType = ModeratedContentType.TRIP_MEMORY, visibility = ModerationVisibility.GROUP)
    private String caption;

    @Size(max = 1000)
    @ModeratedText(contentType = ModeratedContentType.TRIP_MEMORY, visibility = ModerationVisibility.GROUP)
    private String description;

    /**
     * Corrects the capture date, for a photo whose file carried no EXIF.
     * Saving it marks {@code date_source = MANUAL}; {@code created_at} is not
     * touched, so the audit trail still says when the image really arrived.
     */
    private LocalDateTime takenAt;
}
