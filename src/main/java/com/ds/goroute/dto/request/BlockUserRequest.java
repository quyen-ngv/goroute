package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Why somebody was blocked. Optional, and never shown to the person blocked. */
@Data
public class BlockUserRequest {

    /**
     * Deliberately not run through the content filter. Nobody but the person who wrote
     * it and a moderator ever reads it, and filtering somebody's account of why they
     * blocked someone is filtering the complaint.
     */
    @Size(max = 500)
    private String reason;
}
