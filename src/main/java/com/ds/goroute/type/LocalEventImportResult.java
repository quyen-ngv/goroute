package com.ds.goroute.type;

/**
 * What the import did with one item.
 *
 * <ul>
 *     <li>CREATED: no event had this (source, externalId); one was created.</li>
 *     <li>UPDATED: the stored event's content changed and was overwritten.</li>
 *     <li>UNCHANGED: same content hash, or the item's sourceUpdatedAt is older than the stored one.</li>
 *     <li>SKIPPED_EDITED: an admin edited the event in the console and the item did not ask to overwrite.</li>
 *     <li>SKIPPED_DELETED: an admin deleted the event; imports never bring it back.</li>
 *     <li>FAILED: the item broke a field rule; nothing was written for it.</li>
 * </ul>
 */
public enum LocalEventImportResult {
    CREATED,
    UPDATED,
    UNCHANGED,
    SKIPPED_EDITED,
    SKIPPED_DELETED,
    FAILED
}
