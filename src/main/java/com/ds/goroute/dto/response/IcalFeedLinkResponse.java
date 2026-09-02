package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Subscription link a partner pastes into an external calendar / channel manager. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IcalFeedLinkResponse {
    private UUID roomTypeId;
    private String url;
    private String token;
}
