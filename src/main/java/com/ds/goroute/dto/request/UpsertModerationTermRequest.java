package com.ds.goroute.dto.request;

import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertModerationTermRequest {

    @NotBlank(message = "The term is required")
    @Size(max = 200, message = "The term cannot exceed 200 characters")
    private String term;

    @NotNull(message = "A policy group is required")
    private ModerationCategory category;

    @NotNull(message = "An action is required")
    private ModerationAction action;

    @Size(max = 10)
    private String language;

    /** An exemption is a phrase that contains a restricted term but is legitimate. */
    private Boolean isExemption;

    @Size(max = 1000, message = "The note cannot exceed 1000 characters")
    private String note;

    private Boolean isActive;

    /** Guards against two operators overwriting each other's edit. */
    private Long expectedVersion;
}
