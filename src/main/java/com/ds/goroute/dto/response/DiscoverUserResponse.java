package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoverUserResponse {
    private UUID id;
    private String fullName;
    private String username;
    private String avatarUrl;
    private int reviewCount;
    private int followersCount;
    private int followingCount;
}
