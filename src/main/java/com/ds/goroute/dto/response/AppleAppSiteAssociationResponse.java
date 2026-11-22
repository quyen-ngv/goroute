package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * {@code /.well-known/apple-app-site-association}, in the shape iOS reads for Universal
 * Links. Not wrapped in {@code BaseResponse}: the format is Apple's, not ours.
 */
public record AppleAppSiteAssociationResponse(Applinks applinks) {

    public record Applinks(List<Detail> details) {
    }

    /**
     * {@code components} is the iOS 13+ form; {@code paths} is what older iOS reads and is
     * ignored by newer versions when {@code components} is present.
     */
    public record Detail(
            @JsonProperty("appIDs") List<String> appIds,
            List<Map<String, String>> components,
            List<String> paths) {
    }
}
