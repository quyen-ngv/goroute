package com.ds.goroute.service.impl;

import com.ds.goroute.config.AppLinksProperties;
import com.ds.goroute.dto.response.AndroidAssetLinkResponse;
import com.ds.goroute.dto.response.AppleAppSiteAssociationResponse;
import com.ds.goroute.service.AppLinkAssociationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AppLinkAssociationServiceImpl implements AppLinkAssociationService {

    static final String HANDLE_ALL_URLS = "delegate_permission/common.handle_all_urls";

    /** The shared profile link, below the prefix the reverse proxy mounts the app under. */
    static final String PROFILE_PATH = "/u/*";

    private final AppLinksProperties properties;
    private final String pathPrefix;

    public AppLinkAssociationServiceImpl(AppLinksProperties properties,
                                         @Value("${goroute.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.properties = properties;
        this.pathPrefix = pathOf(publicBaseUrl);
    }

    @Override
    public Optional<List<AndroidAssetLinkResponse>> androidAssetLinks() {
        List<String> fingerprints = nonBlank(properties.getAndroidSha256CertFingerprints()).stream()
                .map(fingerprint -> fingerprint.toUpperCase(java.util.Locale.ROOT))
                .toList();
        if (fingerprints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(List.of(new AndroidAssetLinkResponse(
                List.of(HANDLE_ALL_URLS),
                new AndroidAssetLinkResponse.Target(
                        "android_app", properties.getAndroidPackageName().trim(), fingerprints))));
    }

    @Override
    public Optional<AppleAppSiteAssociationResponse> appleAppSiteAssociation() {
        List<String> appIds = nonBlank(properties.getIosAppIds());
        if (appIds.isEmpty()) {
            return Optional.empty();
        }
        String profilePath = pathPrefix + PROFILE_PATH;
        return Optional.of(new AppleAppSiteAssociationResponse(new AppleAppSiteAssociationResponse.Applinks(
                List.of(new AppleAppSiteAssociationResponse.Detail(
                        appIds,
                        List.of(Map.of("/", profilePath, "comment", "Shared profile links")),
                        List.of(profilePath))))));
    }

    /** "https://host/goroute/" -> "/goroute"; a bare origin -> "". */
    static String pathOf(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String path = URI.create(baseUrl.trim()).getPath();
        return path == null ? "" : path.replaceAll("/+$", "");
    }

    private static List<String> nonBlank(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
