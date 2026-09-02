package com.ds.goroute.mapper;

import com.ds.goroute.entity.MarketplaceIcalBooking;
import com.ds.goroute.entity.MarketplaceIcalRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read side of the per-room iCal export plus the feed-token column on {@code room_types}. */
@Mapper
public interface MarketplaceIcalMapper {

    MarketplaceIcalRoom findRoomById(@Param("roomTypeId") UUID roomTypeId);

    MarketplaceIcalRoom findRoomByToken(@Param("token") String token);

    /** CONFIRMED / CHECKED_IN stays of the room whose check-out is on or after {@code fromDate}. */
    List<MarketplaceIcalBooking> findBookedStays(@Param("roomTypeId") UUID roomTypeId,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("limit") int limit);

    /** Inventory days flagged stop-sell inside [fromDate, toDate]. */
    List<LocalDate> findStopSellDays(@Param("roomTypeId") UUID roomTypeId,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate,
                                     @Param("limit") int limit);

    /** Sets the token only while the room has none; returns 0 when another request won the race. */
    int assignTokenIfMissing(@Param("roomTypeId") UUID roomTypeId, @Param("token") String token);

    int replaceToken(@Param("roomTypeId") UUID roomTypeId, @Param("token") String token);
}
