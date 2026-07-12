package com.ds.goroute.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLocationJobCallbackRequest {
    private UUID gorouteJobId;
    private String pythonJobId;
    private String status;
    private String sourceUrl;
    private String platform;
    private JsonNode result;
    private JsonNode error;
}
