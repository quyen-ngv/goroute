package com.ds.goroute.mapper;

import com.ds.goroute.entity.PartnerBookingAggregate;
import com.ds.goroute.entity.PartnerQualitySnapshot;
import com.ds.goroute.entity.PartnerReviewAggregate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface PartnerQualityMapper {
    PartnerBookingAggregate aggregateHotelBookings(@Param("organizationId") UUID organizationId,
                                                   @Param("since") LocalDateTime since,
                                                   @Param("slaMinutes") int slaMinutes);
    PartnerBookingAggregate aggregateActivityOrders(@Param("organizationId") UUID organizationId,
                                                    @Param("since") LocalDateTime since,
                                                    @Param("slaMinutes") int slaMinutes);
    PartnerReviewAggregate aggregateReviews(@Param("organizationId") UUID organizationId,
                                            @Param("since") LocalDateTime since);
    int upsertSnapshot(PartnerQualitySnapshot snapshot);
    PartnerQualitySnapshot findSnapshot(@Param("organizationId") UUID organizationId,
                                        @Param("windowDays") int windowDays);
}
