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
public class GrantSubscriptionRequest {

    @NotBlank
    private String planCode;

    /**
     * Makes the grant repeatable without double-charging: the same key never produces a second
     * period. Left out, the server builds one, which covers a single admin action but cannot
     * protect a retried payment callback -- those must send their own.
     */
    @Size(max = 200)
    private String referenceKey;

    @Size(max = 500)
    private String note;
}
