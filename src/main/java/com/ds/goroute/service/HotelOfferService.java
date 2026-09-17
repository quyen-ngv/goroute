package com.ds.goroute.service;

import com.ds.goroute.dto.request.HotelOfferQuery;
import com.ds.goroute.dto.response.HotelRoomOfferResponse;

import java.util.List;
import java.util.UUID;

/** Guest-facing room and rate offers of one public hotel for a concrete stay. */
public interface HotelOfferService {
    List<HotelRoomOfferResponse> listOffers(UUID hotelId, HotelOfferQuery query);
}
