package com.ds.goroute.thirdparty.apple;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppleTokenInfo {
    private String sub;           // Apple user ID
    private String email;
    private boolean emailVerified;
}
