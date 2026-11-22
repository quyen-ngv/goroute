package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.SavedCategoryVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The whole editable state of a saved category. A null {@code coverUrl} goes back to the
 * newest item's photo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSavedCategoryRequest {

    /** saved_places.category is VARCHAR(50). */
    @NotBlank(message = "A name is required")
    @Size(max = 50)
    @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
    private String name;

    @Size(max = 1000)
    private String coverUrl;

    @NotNull
    private SavedCategoryVisibility visibility;
}
