package com.ds.goroute.mapper;

import com.ds.goroute.entity.BookingChangeRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface BookingChangeRequestMapper {
    int insert(BookingChangeRequest request);
    BookingChangeRequest findById(@Param("id") UUID id);
    List<BookingChangeRequest> findForHotelBooking(@Param("hotelBookingId") UUID hotelBookingId);
    List<BookingChangeRequest> findForActivityOrder(@Param("activityOrderId") UUID activityOrderId);
    List<BookingChangeRequest> findForOrganization(@Param("organizationId") UUID organizationId, @Param("status") String status,
                                                   @Param("hotelIds") List<UUID> hotelIds, @Param("productIds") List<UUID> productIds,
                                                   @Param("limit") int limit, @Param("offset") int offset);
    long countForOrganization(@Param("organizationId") UUID organizationId, @Param("status") String status,
                              @Param("hotelIds") List<UUID> hotelIds, @Param("productIds") List<UUID> productIds);
    long countOpenForOrganization(@Param("organizationId") UUID organizationId);
    int updateStatus(@Param("id") UUID id, @Param("fromStatus") String fromStatus, @Param("status") String status,
                     @Param("respondedBy") UUID respondedBy, @Param("responseNote") String responseNote,
                     @Param("priceAfter") java.math.BigDecimal priceAfter, @Param("now") LocalDateTime now);
}
