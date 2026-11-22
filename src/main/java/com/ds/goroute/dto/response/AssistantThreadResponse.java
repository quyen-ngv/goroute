package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One of the caller's Pengoo threads in their thread list. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantThreadResponse {
    private UUID id;
    /** The thread's title, else the caller's first message cut to 60 characters. */
    private String title;
    /** The last message's text cut to 120 characters; null when the last message is not text. */
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;
    /** The trip the thread's latest applied proposal changed or created, while the caller may still read it. */
    private UUID tripId;
    private String tripName;
}
