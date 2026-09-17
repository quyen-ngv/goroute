package com.ds.goroute.thirdparty.google;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleTokenInfo {
    private String sub;           // Google user ID
    private String email;
    private boolean emailVerified;
    private String name;
    private String picture;
    /**
     * Firebase's {@code firebase.sign_in_provider} claim: which identity provider actually
     * signed this person in ({@code google.com}, {@code apple.com}, ...). Firebase mints the
     * same kind of token whatever the provider, so without this claim an Apple sign-in
     * arriving from the web console would be stored as a Google account.
     */
    private String signInProvider;
}
