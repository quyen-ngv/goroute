package com.ds.goroute.dto.request;

import com.ds.goroute.type.ContentReportReason;
import com.ds.goroute.type.ModeratedContentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** SOC-06a: report any public content, with a reason from a short list. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportContentRequest {

    @NotNull(message = "Content type is required")
    private ModeratedContentType contentType;

    @NotNull(message = "Content id is required")
    private UUID contentId;

    @NotNull(message = "A reason is required")
    private ContentReportReason reason;

    @Size(max = 1000, message = "The note cannot exceed 1000 characters")
    private String note;
}
