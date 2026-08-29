package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripNoteRequest {

    @NotBlank(message = "Content is required")
    @Size(max = 2000, message = "Content must not exceed 2000 characters")
    @ModeratedText(contentType = ModeratedContentType.TRIP_NOTE, visibility = ModerationVisibility.GROUP)
    private String content;

    private Boolean isShared;
}
