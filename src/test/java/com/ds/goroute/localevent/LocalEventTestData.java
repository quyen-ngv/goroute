package com.ds.goroute.localevent;

import com.ds.goroute.entity.Ward;
import com.ds.goroute.localevent.dto.LocalEventImageRequest;
import com.ds.goroute.localevent.dto.LocalEventImportItemRequest;
import com.ds.goroute.localevent.dto.LocalEventVenueRequest;
import com.ds.goroute.service.GeoService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared fixtures: a valid Hanoi concert import item and a geo service that knows every point. */
final class LocalEventTestData {

    /** 2026-10-04 03:00 UTC = 10:00 in Vietnam. */
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 4, 3, 0);

    private LocalEventTestData() {
    }

    static LocalEventImportItemRequest concert() {
        return LocalEventImportItemRequest.builder()
                .source("ticketbox.vn")
                .externalId("evt-123")
                .title("Đêm nhạc Trịnh Công Sơn")
                .category("MUSIC")
                .startAt("2026-11-07T19:30:00+07:00")
                .endAt("2026-11-07T22:00:00+07:00")
                .venue(LocalEventVenueRequest.builder()
                        .name("Nhà hát Lớn Hà Nội")
                        .address("1 Tràng Tiền, Hoàn Kiếm, Hà Nội")
                        .latitude(new BigDecimal("21.0245"))
                        .longitude(new BigDecimal("105.8576"))
                        .build())
                .priceMin(300000L)
                .priceMax(1200000L)
                .images(List.of(LocalEventImageRequest.builder().url("https://images.example.com/a.jpg").build()))
                .build();
    }

    static GeoService geoEverywhere() {
        GeoService geo = mock(GeoService.class);
        Ward ward = new Ward();
        ward.setCode("00004");
        ward.setProvinceCode("01");
        when(geo.resolveWard(any(), any())).thenReturn(Optional.of(ward));
        return geo;
    }
}
