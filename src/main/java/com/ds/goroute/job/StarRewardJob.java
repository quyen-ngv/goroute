package com.ds.goroute.job;

import com.ds.goroute.service.StarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StarRewardJob {
    private final StarService starService;

    @Scheduled(fixedDelayString = "${goroute.jobs.star-rewards-delay-ms:3600000}")
    public void awardCompletedTrips() {
        int count = starService.awardEligibleTripCompletions();
        if (count > 0) log.info("Awarded {} completed-trip star rewards", count);
    }
}
