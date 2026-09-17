package com.ds.goroute.thirdparty.google;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleTokenVerifier {

    public GoogleTokenInfo verify(String idToken) {
        try {
            log.debug("Verifying Firebase ID token (length: {})", idToken != null ? idToken.length() : 0);

            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);

            log.info("Successfully verified Firebase token for user: {}", decodedToken.getEmail());

            return GoogleTokenInfo.builder()
                    .sub(decodedToken.getUid())
                    .email(decodedToken.getEmail())
                    .emailVerified(!explicitlyUnverified(decodedToken))
                    .name(decodedToken.getName())
                    .picture(decodedToken.getPicture())
                    .signInProvider(signInProvider(decodedToken))
                    .build();

        } catch (Exception e) {
            log.error("Failed to verify Firebase token: {}", e.getMessage(), e);
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid Google token");
        }
    }

    /**
     * Firebase nests the originating provider under the {@code firebase} claim. Absent or
     * unreadable, callers fall back to their own default rather than guessing here.
     */
    private String signInProvider(FirebaseToken decodedToken) {
        Object firebase = decodedToken.getClaims() == null ? null : decodedToken.getClaims().get("firebase");
        if (firebase instanceof java.util.Map<?, ?> claims) {
            Object provider = claims.get("sign_in_provider");
            if (provider instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * {@code FirebaseToken.isEmailVerified()} collapses "the provider said no" and "the
     * provider said nothing" into the same {@code false}, which is not safe to act on:
     * refusing every token without the claim would lock out logins that work today.
     * This reads the raw claim so only an explicit {@code false} counts as unverified,
     * and {@link GoogleTokenInfo#isEmailVerified()} therefore means "not explicitly
     * reported as unverified".
     */
    private boolean explicitlyUnverified(FirebaseToken decodedToken) {
        Object raw = decodedToken.getClaims() == null ? null : decodedToken.getClaims().get("email_verified");
        if (raw instanceof Boolean value) {
            return !value;
        }
        if (raw instanceof String value) {
            return "false".equalsIgnoreCase(value.trim());
        }
        return false;
    }
}
