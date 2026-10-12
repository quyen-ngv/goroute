package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Whether the caller may talk to Pengoo, and how many questions are left today. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMeResponse {
    private boolean available;
    private int dailyLimit;
    private int usedToday;
    private String tier;
    private UUID botUserId;
    private String name;
    private String username;
    private String avatarUrl;
}
