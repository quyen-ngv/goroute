package com.ds.goroute.partneronboarding.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * One step's answers.
 *
 * <p>{@code data} is deliberately untyped: the step vocabulary belongs to the wizard, and
 * pinning it to a Java class here would mean a backend release for every screen the design
 * changes. What it is <em>not</em> is unchecked — the moderation advice walks the map before
 * the controller sees it, and the marketplace DTOs validate the same values for real at
 * submit.
 */
@Data
public class SaveStepRequest {

    @NotNull
    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    private Map<String, Object> data;

    /**
     * The draft version the client last read. Absent means "I am the only editor" and is
     * accepted, because a wizard on one device is the common case; sending it is what makes
     * a second device get a conflict instead of a silent overwrite.
     */
    private Long expectedVersion;
}
