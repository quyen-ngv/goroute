package com.ds.goroute.dto.request;

import com.ds.goroute.enums.ConsentPurpose;
import com.ds.goroute.enums.ConsentSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers the person gave on the consent screen or in Settings. TERMS and PRIVACY carry the
 * document version the app showed, which must be the current one; required purposes can only be
 * granted (declining them is signing out, not a stored answer).
 */
@Data
public class ConsentUpdateRequest {

    @NotEmpty
    @Size(max = 10)
    private List<@Valid @NotNull Item> items = new ArrayList<>();

    /** Where the answer was given; CONSENT_SCREEN when absent. */
    private ConsentSource source;

    @Size(max = 20)
    private String platform;

    @Size(max = 40)
    private String appVersion;

    @Data
    public static class Item {
        @NotNull
        private ConsentPurpose purpose;

        @NotNull
        private Boolean granted;

        /** Required for TERMS and PRIVACY: the version the person read. */
        @Size(max = 20)
        private String documentVersion;
    }
}
