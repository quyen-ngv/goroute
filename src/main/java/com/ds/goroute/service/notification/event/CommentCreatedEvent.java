package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CommentCreatedEvent extends TripEvent {
    private String actorName;
    private String activityName;
    private String commentText;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "New Comment";
    }
    
    @Override
    public String getBody() {
        return String.format("%s commented on \"%s\" in %s", actorName, activityName, tripName);
    }
}
