package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NoteDeletedEvent extends TripEvent {
    private String actorName;
    private String tripName;
    private String activityName; // Optional
    
    @Override
    public String getTitle() {
        return "Note Deleted";
    }
    
    @Override
    public String getBody() {
        if (activityName != null) {
            return String.format("%s deleted a note from \"%s\" in %s", actorName, activityName, tripName);
        }
        return String.format("%s deleted a note from %s", actorName, tripName);
    }
}
