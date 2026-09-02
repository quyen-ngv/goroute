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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Writes the commission stamp through {@link PartnerFinanceRepository} only.
 *
 * <p>Deliberately does not go through the hotel/activity services: the stamp touches three
 * accounting columns, must not bump {@code data_version}, and must not be able to fail the booking
 * that triggered it. Every failure path is logged and swallowed — an unstamped row is picked up
 * again by the statement generator, which calls this defensively before it bills a period.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceCommissionServiceImpl implements MarketplaceCommissionService {
    private static final BigDecimal DEFAULT_COMMISSION_PERCENT = new BigDecimal("15.00");

    private final PartnerFinanceRepository finance;
    private final HostOrganizationRepository organizations;
    private final BusinessConfigService businessConfig;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean stampCommission(String bookingType, UUID bookingId) {
        MarketplaceBookingType type = MarketplaceBookingType.parse(bookingType);
        if (type == null || bookingId == null) {
            log.warn("Ignoring commission stamp for unknown booking type {} / id {}", bookingType, bookingId);
            return false;
        }
        try {
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
        } catch (RuntimeException ex) {
            log.error("Could not stamp commission on {} booking {}: {}", type, bookingId, ex.getMessage());
            return false;
        }
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
