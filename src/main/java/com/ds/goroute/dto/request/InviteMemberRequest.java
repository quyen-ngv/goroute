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
public class InviteMemberRequest {
    private String identifier; // Email or username (cho registered user)

    @NotBlank(message = "Role is required")
    private String role;
    
    // Guest member fields
    private Boolean isGuest; // true nếu thêm guest
    private String guestName; // required nếu isGuest = true
    private String guestEmail; // optional
    private String guestPhone; // optional
}
