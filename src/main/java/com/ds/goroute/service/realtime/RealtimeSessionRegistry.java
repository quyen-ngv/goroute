package com.ds.goroute.service.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which signed-in user owns each open STOMP session on this node.
 *
 * <p>The simple broker fans a topic message out per session and does not copy the subscriber's
 * principal onto it, so the outbound access check has to recover the user from the session id.
 * Scanning every connected user for that session on every delivered message was linear in the
 * number of connections; this answers in one lookup. Entries are written when CONNECT is
 * authenticated and removed on DISCONNECT and on the session-closed event, which Spring publishes
 * however the socket ended.
 */
@Service
public class RealtimeSessionRegistry {

    private final Map<String, UUID> userIdsBySessionId = new ConcurrentHashMap<>();

    public void register(String sessionId, UUID userId) {
        if (sessionId == null || userId == null) {
            return;
        }
        userIdsBySessionId.put(sessionId, userId);
    }

    public void remove(String sessionId) {
        if (sessionId != null) {
            userIdsBySessionId.remove(sessionId);
        }
    }

    /** The user behind this session, or null when it never authenticated or has closed. */
    public UUID userIdOf(String sessionId) {
        return sessionId == null ? null : userIdsBySessionId.get(sessionId);
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        remove(event.getSessionId());
    }
}
