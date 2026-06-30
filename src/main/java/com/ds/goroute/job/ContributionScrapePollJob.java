package com.ds.goroute.job;

import com.ds.goroute.event.ContributionScrapePollEvent;
import com.ds.goroute.service.PlaceContributionService;
import com.ds.goroute.type.ContributionGroupStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContributionScrapePollJob {

    private final PlaceContributionService contributionService;

    @Async
    @EventListener
    public void onContributionScrapePoll(ContributionScrapePollEvent event) {
        pollUntilFinished(event.groupId());
    }

    void pollUntilFinished(UUID groupId) {
        int attempts = 0;
        while (attempts < 40) {
            try {
                Thread.sleep(8000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                contributionService.syncScrapingGroup(groupId);
                var group = contributionService.adminGetGroup(groupId);
                ContributionGroupStatus status = group.getStatus();
                if (status == ContributionGroupStatus.COMPLETED
                        || status == ContributionGroupStatus.MERGED_TO_EXISTING
                        || status == ContributionGroupStatus.FAILED) {
                    log.info("Contribution scrape poll finished for group {} with status {}", groupId, status);
                    return;
                }
            } catch (Exception e) {
                log.warn("Contribution scrape poll attempt failed for group {}: {}", groupId, e.getMessage());
            }

            attempts++;
        }

        log.warn("Contribution scrape poll timed out for group {}", groupId);
    }
}
