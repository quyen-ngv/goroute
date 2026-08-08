package com.ds.goroute.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SocialLocationJobDispatcher {
    private final SocialLocationJobService service;

    @Scheduled(fixedDelayString = "${social-location.dispatch-delay-ms:2000}")
    public void dispatch() {
        try {
            service.dispatchQueuedJobs();
        } catch (Exception exception) {
            log.error("Social-location queue dispatch failed", exception);
        }
    }
}
