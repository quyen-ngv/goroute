package com.ds.goroute.type;

/** What a trip note holds (epic 04 §2.4). */
public enum TripNoteType {
    /** Free text in {@code content}. */
    TEXT,
    /** A todo list: {@code content} is its title, items are in {@code trip_note_todo_items}. */
    TODO
}
