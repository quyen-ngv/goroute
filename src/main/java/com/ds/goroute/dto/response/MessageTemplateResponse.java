package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class MessageTemplateResponse {
    private UUID id;
    private UUID organizationId;
    private String title;
    private String body;
    private Integer sortOrder;
}
