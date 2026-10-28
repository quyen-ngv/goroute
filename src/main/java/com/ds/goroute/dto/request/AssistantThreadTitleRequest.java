package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Renames one of the caller's Pengoo threads; 1..80 characters once trimmed (checked by the service). */
@Data
public class AssistantThreadTitleRequest {
    @NotBlank
    @Size(max = 120)
    @ModeratedText(contentType = ModeratedContentType.CHAT_MESSAGE, visibility = ModerationVisibility.CHAT)
    private String title;
}
