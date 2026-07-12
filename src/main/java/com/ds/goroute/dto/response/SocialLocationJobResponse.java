package com.ds.goroute.dto.response;

import com.ds.goroute.type.SocialLocationJobStatus;
import com.fasterxml.jackson.databind.JsonNode;
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
public class SocialLocationJobResponse {
    private UUID id;
    private String sourceUrl;
    private String platform;
    private SocialLocationJobStatus status;
    private String pythonJobId;
    private String language;
    private JsonNode result;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
