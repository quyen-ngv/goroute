package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A correction to something already sent.
 *
 * <p>Goes through the same filter as the original: an edit is not a way round it.
 */
@Data
public class EditMarketplaceMessageRequest {

    @NotBlank
    @Size(max = 10000)
    @ModeratedText(contentType = ModeratedContentType.CHAT_MESSAGE, visibility = ModerationVisibility.CHAT)
    private String content;
}
