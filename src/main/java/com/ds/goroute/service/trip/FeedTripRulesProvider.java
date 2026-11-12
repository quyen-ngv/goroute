package com.ds.goroute.service.trip;

import com.ds.goroute.dto.FeedTripRules;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.utils.AppZones;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Reads {@link FeedTripRules} from the FEED config group on every call, so an edit applies at once. */
@Component
@RequiredArgsConstructor
public class FeedTripRulesProvider {

    private final BusinessConfigService config;
    private final Clock clock = Clock.system(AppZones.VIETNAM);

    /** The rules in force now; null when both are off. */
    public FeedTripRules current() {
        // Trip dates carry no zone and most trips are Vietnamese (AppZones.VIETNAM).
        LocalDate startedBy = config.getBoolean(BusinessConfigKey.FEED_TRIP_HIDE_UPCOMING)
                ? LocalDate.now(clock)
                : null;
        double perDay = config.getDecimal(BusinessConfigKey.FEED_TRIP_MIN_ACTIVITIES_PER_DAY);
        Double minActivitiesPerDay = perDay > 0 ? perDay : null;
        return startedBy == null && minActivitiesPerDay == null
                ? null
                : new FeedTripRules(startedBy, minActivitiesPerDay);
    }
}
