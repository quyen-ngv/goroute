package com.ds.goroute.dto.request;

import com.ds.goroute.type.SavedPostType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Save somebody else's check-in or review to read later. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavePostRequest {

    @NotNull
    private SavedPostType postType;

    @NotNull
    private UUID postId;
}
