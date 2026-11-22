package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where the caller stands with the current legal documents and each optional purpose. An optional
 * purpose is null when the person was never asked, which the app treats as off.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentStateResponse {
    private String termsVersion;
    private String privacyVersion;
    private String acceptedTermsVersion;
    private String acceptedPrivacyVersion;
    private boolean ageConfirmed;
    /** The app must show the consent screen before anything else. */
    private boolean needsAcceptance;
    /**
     * The person used the app before this version (an existing account, or an earlier version
     * accepted), so the screen says the documents changed rather than welcoming them.
     */
    private boolean existingAccount;
    private Boolean analytics;
    private Boolean assistantMemory;
    private Boolean profileSearchIndexing;
}
