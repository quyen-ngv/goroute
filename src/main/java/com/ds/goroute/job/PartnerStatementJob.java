package com.ds.goroute.job;

import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.service.PartnerStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Issues last month's statement for every partner organization on the 3rd of each month.
 *
 * <p>The 3rd, not the 1st: a stay that checks out on the last day of the month is only finalised by
 * the partner a day or two later, and the no-show window itself runs 48 hours past check-out.
 *
 * <p>Each organization is issued through the service proxy in its own transaction, so a bad row in
 * one organization (an unparseable penalty, a lock) is logged and skipped instead of stopping every
 * organization behind it. Re-running the job is safe: the period is keyed by
 * {@code (organization_id, period_start, period_end)} and regeneration preserves disputed lines.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerStatementJob {
    private final HostOrganizationRepository organizations;
    private final PartnerStatementService statementService;

    @Scheduled(cron = "${goroute.jobs.partner-statement-cron:0 30 2 3 * *}")
    public void issuePreviousMonth() {
        List<UUID> ids = organizations.findAllOrganizationIds();
        int issued = 0;
        for (UUID organizationId : ids) {
            try {
                statementService.issuePreviousMonth(organizationId);
                issued++;
            } catch (RuntimeException ex) {
                log.error("Could not issue the monthly statement for organization {}: {}",
                        organizationId, ex.getMessage());
            }
        }
        log.info("Partner statements issued for {}/{} organizations", issued, ids.size());
    }
}
