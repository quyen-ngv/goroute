package com.ds.goroute.service.impl;

import com.ds.goroute.config.AppLinksProperties;
import com.ds.goroute.dto.response.AndroidAssetLinkResponse;
import com.ds.goroute.dto.response.AppleAppSiteAssociationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppLinkAssociationServiceImplTest {

    @Test
    void androidIsNotServedUntilAFingerprintIsConfigured() {
        AppLinksProperties properties = new AppLinksProperties();
        properties.setAndroidSha256CertFingerprints(List.of(" ", ""));

        assertThat(new AppLinkAssociationServiceImpl(properties, "https://onestudy.id.vn/goroute").androidAssetLinks())
                .isEmpty();
    }

    @Test
    void androidStatementIsGooglesFormat() throws Exception {
        AppLinksProperties properties = new AppLinksProperties();
        properties.setAndroidSha256CertFingerprints(List.of(" aa:bb:cc "));

        List<AndroidAssetLinkResponse> links = new AppLinkAssociationServiceImpl(properties, "https://onestudy.id.vn/goroute")
                .androidAssetLinks().orElseThrow();

        assertThat(new ObjectMapper().writeValueAsString(links)).isEqualTo(
                "[{\"relation\":[\"delegate_permission/common.handle_all_urls\"],"
                        + "\"target\":{\"namespace\":\"android_app\",\"package_name\":\"app.ondetour\","
                        + "\"sha256_cert_fingerprints\":[\"AA:BB:CC\"]}}]");
    }

    @Test
    void iosClaimsOnlyTheProfilePathBelowTheProxyPrefix() throws Exception {
        AppleAppSiteAssociationResponse association = new AppLinkAssociationServiceImpl(
                new AppLinksProperties(), "https://onestudy.id.vn/goroute/").appleAppSiteAssociation().orElseThrow();

        String json = new ObjectMapper().writeValueAsString(association);
        assertThat(json)
                .contains("\"appIDs\":[\"9L33V2D6Z9.app.ondetour\"]")
                .contains("\"/\":\"/goroute/u/*\"")
                .contains("\"paths\":[\"/goroute/u/*\"]");
    }

    @Test
    void pathOfABareOriginIsEmpty() {
        assertThat(AppLinkAssociationServiceImpl.pathOf("http://localhost:8080")).isEmpty();
        assertThat(AppLinkAssociationServiceImpl.pathOf("https://onestudy.id.vn/goroute//")).isEqualTo("/goroute");
        assertThat(AppLinkAssociationServiceImpl.pathOf(null)).isEmpty();
    }
}
