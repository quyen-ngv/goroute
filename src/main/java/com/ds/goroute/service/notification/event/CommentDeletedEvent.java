package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CommentDeletedEvent extends TripEvent {
    private String actorName;
    private String activityName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "Comment Deleted";
    }
    
    @Override
    public String getBody() {
        return String.format("%s deleted a comment from \"%s\" in %s", actorName, activityName, tripName);
    }
}
