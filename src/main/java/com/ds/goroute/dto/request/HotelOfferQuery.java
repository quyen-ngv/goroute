package com.ds.goroute.dto.request;

import lombok.Builder;

import java.time.LocalDate;

/** A stay to price every room and rate of one hotel for. */
@Builder
public record HotelOfferQuery(LocalDate checkIn, LocalDate checkOut, int rooms, int adults, int children) {
}
