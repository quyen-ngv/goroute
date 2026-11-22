package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * The editor's save (PUT /posts/{postId}, app and console). The whole editable state every time:
 * an absent excerpt is regenerated from the body, absent places clear them.
 *
 * <p>The size limits here are hard ceilings; POST.MAX_BODY_LENGTH and POST.MAX_PLACES are checked
 * in the service against the live configuration.
 */
@Data
public class UpdatePostRequest {

    @NotBlank
    @Size(max = 200)
    @ModeratedText(contentType = ModeratedContentType.POST, visibility = ModerationVisibility.PUBLIC)
    private String title;

    @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.POST, visibility = ModerationVisibility.PUBLIC)
    private String excerpt;

    @Size(max = 100000)
    @ModeratedText(contentType = ModeratedContentType.POST, visibility = ModerationVisibility.PUBLIC)
    private String body;

    /** One of this post's live media, or null for no cover. */
    private UUID coverMediaId;

    @Size(max = 100)
    private List<UUID> placeIds;

    /** Null keeps the current one. */
    private ContentVisibility visibility;

    /** The version the editor loaded; a different stored one answers 409 instead of overwriting. */
    @NotNull
    private Integer expectedVersion;
}
