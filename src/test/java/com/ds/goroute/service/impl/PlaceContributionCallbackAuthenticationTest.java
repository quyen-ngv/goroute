package com.ds.goroute.service.impl;

import com.ds.goroute.config.InternalApiProperties;
import com.ds.goroute.config.filter.InternalApiAuthenticationFilter;
import com.ds.goroute.entity.PlaceContributionGroup;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PlaceContributionMapper;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.thirdparty.scrape.ScrapeContributionJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeJobTriggerResponse;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.ContributionGroupStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceContributionCallbackAuthenticationTest {

    @Mock private PlaceContributionMapper contributionMapper;
    @Mock private PlaceRepository placeRepository;
    @Mock private ScrapeServiceClient scrapeServiceClient;
    @Mock private InternalApiProperties internalApiProperties;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private PlaceContributionServiceImpl service;

    private PlaceContributionGroup group;

    @BeforeEach
    void setUp() {
        group = PlaceContributionGroup.builder()
                .id(UUID.randomUUID())
                .googleMapsUrl("https://www.google.com/maps/search/?api=1&query=test")
                .status(ContributionGroupStatus.PENDING)
                .build();
        when(contributionMapper.findGroupById(group.getId())).thenReturn(group);
        when(contributionMapper.claimGroupForScraping(eq(group.getId()), any())).thenReturn(1);
        when(contributionMapper.findContributionsByGroupId(group.getId())).thenReturn(List.of());
        when(contributionMapper.findPendingReviewsByGroupId(group.getId())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://goroute-app:8080");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvedContributionPassesTheActualInternalCallbackFilter() throws Exception {
        when(internalApiProperties.scrapeCallbackToken()).thenReturn("scrape-secret");
        ScrapeJobTriggerResponse trigger = new ScrapeJobTriggerResponse();
        trigger.setJobId(UUID.randomUUID().toString());
        when(scrapeServiceClient.triggerContributionScrape(any())).thenReturn(trigger);

        service.adminApprove(group.getId());

        ArgumentCaptor<ScrapeContributionJobRequest> dispatched =
                ArgumentCaptor.forClass(ScrapeContributionJobRequest.class);
        verify(scrapeServiceClient).triggerContributionScrape(dispatched.capture());
        var callback = dispatched.getValue().getImportConfig();
        assertThat(callback.getUrl()).isEqualTo(
                "http://goroute-app:8080/v1/api/internal/places/import/contribution");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/v1/api/internal/places/import/contribution");
        callback.getHeaders().forEach(request::addHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new InternalApiAuthenticationFilter(
                new InternalApiProperties("ai-secret", "scrape-secret"), new ObjectMapper())
                .doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
        assertThat(group.getStatus()).isEqualTo(ContributionGroupStatus.SCRAPING);
        assertThat(group.getScrapeJobId()).isEqualTo(trigger.getJobId());
    }

    @Test
    void missingTokenRefusesToQueueAnUnauthenticatedImportAndReleasesApproval() {
        when(internalApiProperties.scrapeCallbackToken()).thenReturn(" ");

        assertThatThrownBy(() -> service.adminApprove(group.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Scrape callback authentication is not configured");

        verify(scrapeServiceClient, never()).triggerContributionScrape(any());
        assertThat(group.getStatus()).isEqualTo(ContributionGroupStatus.PENDING);
    }
}
