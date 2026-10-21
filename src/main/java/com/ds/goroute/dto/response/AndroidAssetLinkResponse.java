package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One statement of {@code /.well-known/assetlinks.json}, in the shape Android's App Links
 * verifier reads (Digital Asset Links). Not wrapped in {@code BaseResponse}: the format is
 * Google's, not ours.
 */
public record AndroidAssetLinkResponse(List<String> relation, Target target) {

    public record Target(
            String namespace,
            @JsonProperty("package_name") String packageName,
            @JsonProperty("sha256_cert_fingerprints") List<String> sha256CertFingerprints) {
    }
}
