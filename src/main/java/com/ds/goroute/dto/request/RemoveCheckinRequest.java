package com.ds.goroute.dto.request;

import com.ds.goroute.type.ModerationCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** An admin deleting an unsuitable check-in. */
@Data
public class RemoveCheckinRequest {

    /** Shown to the author, so it must say something they can act on. */
    @NotBlank(message = "A reason is required")
    @Size(max = 500)
    private String reason;

    /** Optional: only set when the removal matches one of the standing policy groups. */
    private ModerationCategory category;

    /** Also delete the place review the check-in carries (its rating leaves the place average). */
    private boolean deleteReview;
}
