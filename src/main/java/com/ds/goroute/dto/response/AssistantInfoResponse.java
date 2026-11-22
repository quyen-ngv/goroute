package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Pengoo as seen by one viewer of one conversation. Only set on a TRIP or ASSISTANT conversation
 * opened on its own (not in inbox lists), because availability is per viewer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantInfoResponse {
    private UUID userId;
    private String name;
    private String username;
    private String avatarUrl;
    /** The viewer may ask (feature on, or a beta user). */
    private boolean available;
    /** The viewer may apply proposals to the conversation's trip. */
    private boolean canEditTrip;
    /** The question being answered (or waiting) right now, so a thread opened mid-turn shows it. */
    private AssistantTurnResponse activeTurn;
}
