package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertPlaceCollectionRequest {

    @NotBlank(message = "A name is required")
    @Size(max = 200)
    @ModeratedText(contentType = ModeratedContentType.PLACE_COLLECTION, visibility = ModerationVisibility.PUBLIC)
    private String name;

    @Size(max = 2000)
    @ModeratedText(contentType = ModeratedContentType.PLACE_COLLECTION, visibility = ModerationVisibility.PUBLIC)
    private String description;

    @Size(max = 1000)
    private String coverImageUrl;

    private ContentVisibility visibility = ContentVisibility.PRIVATE;
}
