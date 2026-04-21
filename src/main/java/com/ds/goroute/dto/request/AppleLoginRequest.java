package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
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
}
