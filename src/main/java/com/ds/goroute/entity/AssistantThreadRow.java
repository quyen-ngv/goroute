package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of a person's Pengoo thread list, as read; not a table. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantThreadRow {
    private UUID id;
    /** The stored title, else the owner's first message (one line, 60 characters). */
    private String title;
    /** The last message's text cut to 120 characters; null when it is not text. */
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;
    /** The trip of the thread's latest applied proposal; the service checks the owner may still read it. */
    private UUID tripId;
    private String tripName;
}
