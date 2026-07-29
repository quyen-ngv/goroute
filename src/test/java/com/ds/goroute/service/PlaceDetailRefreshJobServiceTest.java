package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreatePlaceDetailRefreshJobRequest;
import com.ds.goroute.dto.request.PlaceDetailRefreshJobEventRequest;
import com.ds.goroute.entity.PlaceImportJob;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.thirdparty.scrape.ScrapeJobTriggerResponse;
import com.ds.goroute.thirdparty.scrape.ScrapePlaceDetailRefreshJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceDetailRefreshJobServiceTest {
    private PlaceImportJobMapper jobMapper;
    private ScrapeServiceClient scrapeServiceClient;
    private PlaceDetailRefreshJobService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(PlaceImportJobMapper.class);
        scrapeServiceClient = mock(ScrapeServiceClient.class);
        service = new PlaceDetailRefreshJobService(jobMapper, scrapeServiceClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://goroute-app:8080");
        ReflectionTestUtils.setField(service, "callbackToken", "internal-token");
    }

    @Test
    void backendCreatesOwnedJobAndTriggersInternalPythonWorker() {
        ScrapeJobTriggerResponse trigger = new ScrapeJobTriggerResponse();
        trigger.setJobId("python-job-id");
        when(scrapeServiceClient.triggerPlaceDetailRefreshJob(any())).thenReturn(trigger);

        var response = service.trigger(CreatePlaceDetailRefreshJobRequest.builder()
                .maxPlaces(10)
                .headless(true)
                .continueOnError(true)
                .build());

        ArgumentCaptor<PlaceImportJob> jobCaptor = ArgumentCaptor.forClass(PlaceImportJob.class);
        verify(jobMapper).insertJob(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getSourceType()).isEqualTo(PlaceImportSourceType.PLACE_DETAILS_REFRESH);
        assertThat(response.getStatus()).isEqualTo(PlaceImportJobStatus.PROCESSING);
        assertThat(response.getPythonJobId()).isEqualTo("python-job-id");

        ArgumentCaptor<ScrapePlaceDetailRefreshJobRequest> requestCaptor =
                ArgumentCaptor.forClass(ScrapePlaceDetailRefreshJobRequest.class);
        verify(scrapeServiceClient).triggerPlaceDetailRefreshJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCallbackUrl())
                .isEqualTo("http://goroute-app:8080/v1/api/internal/place-import-jobs/place-details-refresh/events");
        assertThat(requestCaptor.getValue().getMaxPlaces()).isEqualTo(10);
    }

    @Test
    void callbackPersistsProgressAndCompletion() {
        UUID jobId = UUID.randomUUID();
        PlaceImportJob job = PlaceImportJob.builder()
                .id(jobId)
                .sourceType(PlaceImportSourceType.PLACE_DETAILS_REFRESH)
                .status(PlaceImportJobStatus.PROCESSING)
                .pythonJobId("python-job-id")
                .build();
        when(jobMapper.findJobById(jobId)).thenReturn(job);

        service.acceptEvent(PlaceDetailRefreshJobEventRequest.builder()
                .jobId(jobId)
                .pythonJobId("python-job-id")
                .eventType("JOB_COMPLETED")
                .databaseCount(20)
                .eligibleCount(15)
                .processedCount(15)
                .successCount(13)
                .failedCount(2)
                .build());

        assertThat(job.getStatus()).isEqualTo(PlaceImportJobStatus.COMPLETED);
        assertThat(job.getTotalItems()).isEqualTo(20);
        assertThat(job.getCompletedCount()).isEqualTo(13);
        assertThat(job.getFailedCount()).isEqualTo(2);
        verify(jobMapper).updateJob(job);
    }
}
