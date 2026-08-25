package com.ds.goroute.controller;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.job.ImageMigrationJob;
import com.ds.goroute.job.ReviewCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ImageMigrationControllerTest {

    @Test
    void doesNotExposeInternalFailureDetailsFromMigrationEndpoints() {
        ImageMigrationJob imageMigrationJob = mock(ImageMigrationJob.class);
        ReviewCleanupJob reviewCleanupJob = mock(ReviewCleanupJob.class);
        ImageMigrationController controller = new ImageMigrationController(imageMigrationJob, reviewCleanupJob);
        ReflectionTestUtils.setField(controller, "httpServletRequest", new MockHttpServletRequest());
        doThrow(new IllegalStateException("database password leaked")).when(imageMigrationJob).migratePlaces();

        var response = controller.migratePlaces();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMeta().getCode()).isEqualTo(ErrorConstant.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMeta().getMessage()).doesNotContain("database password leaked");
        assertThat(response.getBody().getData()).isNull();
    }
}
