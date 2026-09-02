package com.ds.goroute.job;

import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.service.PartnerQualityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Recomputes the rolling quality snapshot of every partner organization once a day.
 *
 * <p>Each organization is computed through the service proxy in its own transaction, so a
 * failing aggregate (bad data, a lock) is logged and skipped rather than aborting the run
 * for every organization behind it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerQualityJob {
    private final HostOrganizationRepository organizations;
    private final PartnerQualityService qualityService;

    @Scheduled(cron = "${goroute.jobs.partner-quality-cron:0 0 3 * * *}")
    public void recomputeAll() {
        List<UUID> ids = organizations.findAllOrganizationIds();
        int computed = 0;
        for (UUID organizationId : ids) {
            try {
                qualityService.compute(organizationId);
                computed++;
            } catch (RuntimeException ex) {
                log.error("Could not compute quality snapshot for organization {}: {}", organizationId, ex.getMessage());
            }
        }
        log.info("Partner quality snapshots recomputed for {}/{} organizations", computed, ids.size());
    }
}
