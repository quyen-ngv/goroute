package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Editing a check-in.
 *
 * <p>The place is not editable. Moving a check-in somewhere else would mean recomputing
 * the averages of two places and rewriting a passport entry, and the history stops being
 * traceable; deleting and checking in again says the same thing without any of that.
 *
 * <p>The caption carries the same annotation as on create, so an edit goes through the
 * same filter -- otherwise editing would be the obvious way around it.
 */
@Data
public class UpdateUserCheckinRequest {

    @Size(max = 5000)
    @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
    private String caption;

    @Size(max = 300)
    @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
    private String customName;

    @Min(1) @Max(5) private Integer overallRating;
    @Min(1) @Max(5) private Integer foodRating;
    @Min(1) @Max(5) private Integer priceRating;
    @Min(1) @Max(5) private Integer ambianceRating;
    @Min(1) @Max(5) private Integer serviceRating;

    /**
     * A check-in is a photo-backed visit, so editing replaces the ordered photo set as
     * one atomic operation: keep an existing URL, remove it, or add an uploaded one.
     */
    @NotEmpty(message = "A check-in needs at least one photo")
    @Valid
    private List<CreateUserCheckinRequest.CheckinPhotoInput> photos;

    private ContentVisibility visibility;
}
