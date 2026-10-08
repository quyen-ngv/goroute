package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppleLoginRequest {
    @NotBlank(message = "Identity token is required")
    private String identityToken;
    
    @NotBlank(message = "Apple user ID is required")
    private String appleUserId;
    
    private String guestId;

    /**
     * Name from the user's Apple ID. Apple gives it to the app only on the first
     * authorization and the user may hide it, so it is often absent.
     */
    @Size(max = 255)
    private String fullName;
}
