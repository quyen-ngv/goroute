package com.ds.goroute.service;

import com.ds.goroute.config.PlaceImportWatchdogProperties;
import com.ds.goroute.entity.PlaceImportJob;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.thirdparty.scrape.ScrapeJobStatusResponse;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceImportJobWatchdogServiceTest {
    private PlaceImportJobMapper jobMapper;
    private ScrapeServiceClient scrapeServiceClient;
    private PlaceImportJobWatchdogService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(PlaceImportJobMapper.class);
        scrapeServiceClient = mock(ScrapeServiceClient.class);
        service = new PlaceImportJobWatchdogService(
                jobMapper, scrapeServiceClient, new PlaceImportWatchdogProperties());
    }

    @Test
    void closesJobWhenWorkerAlreadyFinishedIt() {
        PlaceImportJob job = stalledJob(LocalDateTime.now().minusMinutes(20));
        when(jobMapper.findStalledWorkerJobs(any(), anyInt())).thenReturn(List.of(job));
        when(scrapeServiceClient.pollJob("py-1")).thenReturn(workerStatus("completed", null));
        when(jobMapper.markJobTerminal(any(), any(), any(), any())).thenReturn(1);

        assertThat(service.reconcileStalledJobs()).isEqualTo(1);
        verify(jobMapper).markJobTerminal(
                eq(job.getId()), eq(PlaceImportJobStatus.COMPLETED), any(), any());
    }

    @Test
    void reportsWorkerFailureMessage() {
        PlaceImportJob job = stalledJob(LocalDateTime.now().minusMinutes(20));
        when(jobMapper.findStalledWorkerJobs(any(), anyInt())).thenReturn(List.of(job));
        when(scrapeServiceClient.pollJob("py-1")).thenReturn(workerStatus("failed", "Chrome crashed"));
        when(jobMapper.markJobTerminal(any(), any(), any(), any())).thenReturn(1);

        service.reconcileStalledJobs();

        verify(jobMapper).markJobTerminal(
                eq(job.getId()),
                eq(PlaceImportJobStatus.FAILED),
                eq("Scrape worker reported: Chrome crashed"),
                any());
    }

    @Test
    void leavesJobAloneWhileWorkerIsStillRunningIt() {
        PlaceImportJob job = stalledJob(LocalDateTime.now().minusHours(3));
        when(jobMapper.findStalledWorkerJobs(any(), anyInt())).thenReturn(List.of(job));
        when(scrapeServiceClient.pollJob("py-1")).thenReturn(workerStatus("running", null));

        assertThat(service.reconcileStalledJobs()).isZero();
        verify(jobMapper, never()).markJobTerminal(any(), any(), any(), any());
    }

    @Test
    void keepsUnknownJobOpenUntilAbandonThreshold() {
        PlaceImportJob job = stalledJob(LocalDateTime.now().minusMinutes(20));
        when(jobMapper.findStalledWorkerJobs(any(), anyInt())).thenReturn(List.of(job));
        when(scrapeServiceClient.pollJob("py-1")).thenReturn(null);

        assertThat(service.reconcileStalledJobs()).isZero();
        verify(jobMapper, never()).markJobTerminal(any(), any(), any(), any());
    }

    @Test
    void failsJobTheWorkerNoLongerKnowsAfterAbandonThreshold() {
        PlaceImportJob job = stalledJob(LocalDateTime.now().minusHours(2));
        when(jobMapper.findStalledWorkerJobs(any(), anyInt())).thenReturn(List.of(job));
        when(scrapeServiceClient.pollJob("py-1")).thenReturn(null);
        when(jobMapper.markJobTerminal(any(), any(), any(), any())).thenReturn(1);

        assertThat(service.reconcileStalledJobs()).isEqualTo(1);
        verify(jobMapper).markJobTerminal(
                eq(job.getId()), eq(PlaceImportJobStatus.FAILED), any(), any());
    }

    @Test
    void cancelsAbandonedJobThatHadCancelRequested() {
        PlaceImportJob job = stalledJob(LocalDateTime.now().minusHours(2));
        job.setCancelRequested(true);
        when(jobMapper.findStalledWorkerJobs(any(), anyInt())).thenReturn(List.of(job));
        when(scrapeServiceClient.pollJob("py-1")).thenReturn(null);
        when(jobMapper.markJobTerminal(any(), any(), any(), any())).thenReturn(1);

        service.reconcileStalledJobs();

        verify(jobMapper).markJobTerminal(
                eq(job.getId()), eq(PlaceImportJobStatus.CANCELLED), any(), any());
    }

    @Test
    void doesNotCountJobsAlreadyClosedByAnArrivingCallback() {
        PlaceImportJob job = stalledJob(LocalDateTime.now().minusMinutes(20));
        when(jobMapper.findStalledWorkerJobs(any(), anyInt())).thenReturn(List.of(job));
        when(scrapeServiceClient.pollJob("py-1")).thenReturn(workerStatus("completed", null));
        when(jobMapper.markJobTerminal(any(), any(), any(), any())).thenReturn(0);

        assertThat(service.reconcileStalledJobs()).isZero();
    }

    @Test
    void propertiesRejectAbandonThresholdShorterThanSilentThreshold() {
        PlaceImportWatchdogProperties properties = new PlaceImportWatchdogProperties();
        properties.setSilentAfter(Duration.ofMinutes(30));
        properties.setAbandonAfter(Duration.ofMinutes(10));

        assertThat(properties.isAbandonAfterValid()).isFalse();
    }

    private PlaceImportJob stalledJob(LocalDateTime updatedAt) {
        return PlaceImportJob.builder()
                .id(UUID.randomUUID())
                .sourceType(PlaceImportSourceType.PLACE_DETAILS_REFRESH)
                .status(PlaceImportJobStatus.PROCESSING)
                .pythonJobId("py-1")
                .cancelRequested(false)
                .createdAt(updatedAt)
                .updatedAt(updatedAt)
                .build();
    }

    private ScrapeJobStatusResponse workerStatus(String status, String errorMessage) {
        ScrapeJobStatusResponse response = new ScrapeJobStatusResponse();
        response.setStatus(status);
        if (errorMessage != null) {
            ScrapeJobStatusResponse.ErrorDetail error = new ScrapeJobStatusResponse.ErrorDetail();
            error.setMessage(errorMessage);
            response.setError(error);
        }
        return response;
    }
}
