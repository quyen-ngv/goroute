package com.ds.goroute.service.impl;

import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PartnerFinanceRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.MarketplaceCommissionService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MarketplaceBookingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Writes the commission stamp through {@link PartnerFinanceRepository} only.
 *
 * <p>Deliberately does not go through the hotel/activity services: the stamp touches three
 * accounting columns, must not bump {@code data_version}, and must not be able to fail the booking
 * that triggered it. Every failure path is logged and swallowed — an unstamped row is picked up
 * again by the statement generator, which calls this defensively before it bills a period.
 *
 * <p>Swallowing the exception is only half of that promise. On PostgreSQL a failed statement aborts
 * the whole transaction, so a caught error inside the caller's transaction leaves every later
 * statement failing with "current transaction is aborted" — the booking would 500 anyway. The write
 * therefore runs inside its own savepoint, which is rolled back on failure and leaves the caller's
 * transaction usable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceCommissionServiceImpl implements MarketplaceCommissionService {
    private static final BigDecimal DEFAULT_COMMISSION_PERCENT = new BigDecimal("15.00");

    private final PartnerFinanceRepository finance;
    private final HostOrganizationRepository organizations;
    private final BusinessConfigService businessConfig;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean stampCommission(String bookingType, UUID bookingId) {
        MarketplaceBookingType type = MarketplaceBookingType.parse(bookingType);
        if (type == null || bookingId == null) {
            log.warn("Ignoring commission stamp for unknown booking type {} / id {}", bookingType, bookingId);
            return false;
        }
        // NESTED, not REQUIRES_NEW: the booking row this stamps is usually still uncommitted in the
        // caller's transaction, so a second connection could not see it. A savepoint stays on the
        // caller's connection, and rolling back to it undoes only the failed stamp.
        TransactionTemplate savepoint = new TransactionTemplate(transactionManager);
        savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        try {
            return Boolean.TRUE.equals(savepoint.execute(status -> stamp(type, bookingId)));
        } catch (RuntimeException ex) {
            log.error("Could not stamp commission on {} booking {}: {}", type, bookingId, ex.getMessage());
            return false;
        }
    }

    private boolean stamp(MarketplaceBookingType type, UUID bookingId) {
        UUID organizationId = (type == MarketplaceBookingType.HOTEL
                ? finance.findHotelBookingOrganization(bookingId)
                : finance.findActivityOrderOrganization(bookingId)).orElse(null);
        if (organizationId == null) {
            log.warn("Cannot stamp commission: {} booking {} no longer exists", type, bookingId);
            return false;
        }
        BigDecimal percent = currentCommissionPercent(organizationId);
        String ruleVersion = currentRuleVersion();
        int updated = type == MarketplaceBookingType.HOTEL
                ? finance.stampHotelBookingCommission(bookingId, percent, ruleVersion)
                : finance.stampActivityOrderCommission(bookingId, percent, ruleVersion);
        return updated == 1;
    }

    @Override
    public BigDecimal currentCommissionPercent(UUID organizationId) {
        return organizations.findById(organizationId)
                .map(HostOrganization::getCommissionPercent)
                .orElse(DEFAULT_COMMISSION_PERCENT);
    }

    @Override
    public String currentRuleVersion() {
        String version = businessConfig.getText(BusinessConfigKey.MARKETPLACE_COMMISSION_RULE_VERSION);
        return version == null || version.isBlank()
                ? BusinessConfigKey.MARKETPLACE_COMMISSION_RULE_VERSION.defaultValue()
                : version.trim();
    }
}
