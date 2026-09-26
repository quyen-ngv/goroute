package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One person's one reaction to one message. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceMessageReaction {

    private UUID messageId;
    private UUID userId;
    private String emoji;
    private String userName;
}
