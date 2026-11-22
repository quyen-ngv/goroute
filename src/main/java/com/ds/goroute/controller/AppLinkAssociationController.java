package com.ds.goroute.controller;

import com.ds.goroute.dto.response.AndroidAssetLinkResponse;
import com.ds.goroute.dto.response.AppleAppSiteAssociationResponse;
import com.ds.goroute.service.AppLinkAssociationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The files Android and iOS fetch to decide whether the app may open this host's links.
 * They must sit at the root of the domain ({@code https://<host>/.well-known/...}), so the
 * reverse proxy forwards exactly these two paths here without the {@code /goroute} prefix.
 * Both answer 404 until configured, which leaves the links opening in the browser.
 */
@RestController
@RequiredArgsConstructor
public class AppLinkAssociationController {

    private final AppLinkAssociationService appLinkAssociationService;

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AndroidAssetLinkResponse>> androidAssetLinks() {
        return ResponseEntity.of(appLinkAssociationService.androidAssetLinks());
    }

    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AppleAppSiteAssociationResponse> appleAppSiteAssociation() {
        return ResponseEntity.of(appLinkAssociationService.appleAppSiteAssociation());
    }
}
