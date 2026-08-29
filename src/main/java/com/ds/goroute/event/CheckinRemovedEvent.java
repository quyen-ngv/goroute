package com.ds.goroute.event;

import java.util.UUID;

/** Published after a check-in is hidden, so derived views can drop it. */
public record CheckinRemovedEvent(UUID checkinId, UUID userId) {
}
