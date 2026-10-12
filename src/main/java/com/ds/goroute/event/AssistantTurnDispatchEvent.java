package com.ds.goroute.event;

import java.util.UUID;

/** A turn became RUNNING; after commit it is sent to the AI worker. */
public record AssistantTurnDispatchEvent(UUID turnId) {
}
