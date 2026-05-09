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
}
