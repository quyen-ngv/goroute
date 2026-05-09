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
                    .emailVerified(decodedToken.isEmailVerified())
                    .name(decodedToken.getName())
                    .picture(decodedToken.getPicture())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to verify Firebase token: {}", e.getMessage(), e);
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid Google token");
        }
    }
}
