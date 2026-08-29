package com.ds.goroute.event;

import java.util.UUID;

/**
 * Published after a check-in is safely stored.
 *
 * <p>Rewards and passport entries are consequences, not part of the submission. The author
 * cares that their photos and words were kept; a stamp arriving a few seconds later is
 * fine, and a failure while granting one must never undo the check-in itself.
 */
public record CheckinCreatedEvent(UUID checkinId, UUID userId) {
}
