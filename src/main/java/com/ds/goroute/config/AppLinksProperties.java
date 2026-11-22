package com.ds.goroute.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Who may open the shared links ({@code /u/{username}}) in the app instead of the browser:
 * the Android App Links and iOS Universal Links association files under {@code /.well-known/}.
 * Not secret -- both files are public by design.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "goroute.app-links")
public class AppLinksProperties {

    @NotBlank
    private String androidPackageName = "app.ondetour";

    /**
     * SHA-256 fingerprints (colon-separated hex) of every certificate that signs a build
     * people install: the Play app-signing key from Play Console (Setup > App signing), plus
     * the upload or debug key for side-loaded test builds. While empty, the Android file is
     * not served and Android keeps opening the links in the browser.
     */
    private List<String> androidSha256CertFingerprints = List.of();

    /** {@code <Team ID>.<bundle id>} of each iOS app allowed to open the links. */
    private List<String> iosAppIds = List.of("9L33V2D6Z9.app.ondetour");
}
