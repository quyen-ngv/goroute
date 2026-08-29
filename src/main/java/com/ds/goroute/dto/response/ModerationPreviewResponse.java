package com.ds.goroute.dto.response;

import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** What the current list would do with a passage, and why. */
@Data
@Builder
public class ModerationPreviewResponse {
    private ModerationAction action;
    private ModerationCategory category;
    private UUID matchedTermId;
    private String matchedTerm;
    /** Human-readable explanation, already localised. */
    private String message;
}
