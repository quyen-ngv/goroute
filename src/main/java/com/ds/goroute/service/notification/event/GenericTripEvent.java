package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GenericTripEvent extends TripEvent {
    @Override
    public String getTitle() {
        return getType().name();
    }

    @Override
    public String getBody() {
        return getType().name();
    }
}
