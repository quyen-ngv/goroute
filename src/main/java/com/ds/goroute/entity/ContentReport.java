package com.ds.goroute.entity;

import com.ds.goroute.type.ContentReportReason;
import com.ds.goroute.type.ContentReportStatus;
import com.ds.goroute.type.ModeratedContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A user report (SOC-06a). Unique per reporter and content. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentReport {
    private UUID id;
    private ModeratedContentType contentType;
    private UUID contentId;
    private UUID reporterId;
    private ContentReportReason reason;
    private String note;
    private UUID flagId;
    private ContentReportStatus status;
    private LocalDateTime createdAt;
}
