package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AddCollectionItemRequest {

    private UUID placeId;

    @NotBlank(message = "A display name is required")
    @Size(max = 300)
    @ModeratedText(contentType = ModeratedContentType.PLACE_COLLECTION, visibility = ModerationVisibility.PUBLIC)
    private String displayName;

    @Size(max = 1000)
    @ModeratedText(contentType = ModeratedContentType.PLACE_COLLECTION, visibility = ModerationVisibility.PUBLIC)
    private String displayNote;

    private BigDecimal latitude;
    private BigDecimal longitude;
}
