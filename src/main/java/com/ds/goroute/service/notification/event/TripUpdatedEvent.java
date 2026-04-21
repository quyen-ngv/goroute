package com.ds.goroute.service.notification.event;

import com.ds.goroute.type.NotificationType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TripUpdatedEvent extends TripEvent {
    private String tripName;
    private String actorName;
    
    @Override
    public String getTitle() {
        return "Trip Updated";
    }
    
    @Override
    public String getBody() {
        return String.format("%s updated the trip \"%s\"", actorName, tripName);
    }
}
