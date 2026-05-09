package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TripDeletedEvent extends TripEvent {
    private String tripName;
    private String actorName;
    
    @Override
    public String getTitle() {
        return "Trip Deleted";
    }
    
    @Override
    public String getBody() {
        return String.format("%s deleted the trip \"%s\"", actorName, tripName);
    }
}
