package com.ds.goroute.event;

import java.util.UUID;

/** A trip's group chat was just created (with its trip). Pengoo greets it after commit. */
public record TripConversationCreatedEvent(UUID conversationId, UUID tripId) {
}
