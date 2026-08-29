package com.ds.goroute.dto.response;

import com.ds.goroute.type.ContentReportReason;
import com.ds.goroute.type.ContentReportStatus;
import com.ds.goroute.type.ModeratedContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentReportResponse {
    private UUID id;
    private ModeratedContentType contentType;
    private UUID contentId;
    private ContentReportReason reason;
    private ContentReportStatus status;
    private LocalDateTime createdAt;
}
