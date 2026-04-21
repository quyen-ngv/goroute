package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MemberRemovedEvent extends TripEvent {
    private UUID removedMemberId;
    private String removedMemberName;
    private String actorName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "Member Removed";
    }
    
    @Override
    public String getBody() {
        return String.format("%s removed %s from %s", actorName, removedMemberName, tripName);
    }
}
