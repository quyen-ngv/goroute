package com.ds.goroute.service.assistant;

import java.util.UUID;

/** Who Pengoo is: the fixed users row seeded by V228. */
public final class AssistantIdentity {

    public static final UUID BOT_USER_ID = UUID.fromString("5e9d0000-0000-4000-8000-00000000a1b0");
    public static final String NAME = "Pengoo";
    public static final String USERNAME = "pengoo";

    private AssistantIdentity() {
    }

    public static boolean isAssistant(UUID userId) {
        return BOT_USER_ID.equals(userId);
    }
}
