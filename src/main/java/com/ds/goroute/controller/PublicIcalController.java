package com.ds.goroute.controller;

import com.ds.goroute.service.IcalFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Unauthenticated iCal feed: the token in the path is the credential. Calendar clients poll this
 * URL, so it answers with a plain {@code text/calendar} body rather than the JSON envelope.
 */
@RestController
@RequestMapping("/v1/api/public/ical")
@RequiredArgsConstructor
public class PublicIcalController {

    private static final MediaType TEXT_CALENDAR_UTF8 = MediaType.parseMediaType("text/calendar; charset=utf-8");

    private final IcalFeedService service;

    @GetMapping("/rooms/{token}.ics")
    public ResponseEntity<String> roomFeed(@PathVariable String token) {
        String body = service.renderFeed(token);
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"room-calendar.ics\"")
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .body(body);
    }
}
