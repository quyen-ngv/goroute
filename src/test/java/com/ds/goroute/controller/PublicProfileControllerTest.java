package com.ds.goroute.controller;

import com.ds.goroute.dto.response.PublicPage;
import com.ds.goroute.service.PublicPageService;
import com.ds.goroute.service.PublicProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicProfileControllerTest {

    @Test
    void vietnameseUnlessTheBrowserAsksForSomethingElseFirst() {
        assertThat(PublicProfileController.prefersVietnamese(null)).isTrue();
        assertThat(PublicProfileController.prefersVietnamese("")).isTrue();
        assertThat(PublicProfileController.prefersVietnamese("vi-VN,vi;q=0.9,en;q=0.8")).isTrue();
        assertThat(PublicProfileController.prefersVietnamese("*")).isTrue();
        assertThat(PublicProfileController.prefersVietnamese("en-US,en;q=0.9,vi;q=0.8")).isFalse();
        assertThat(PublicProfileController.prefersVietnamese("ja")).isFalse();
    }

    @Test
    void anUnknownUsernameAnswers404WithThePage() {
        PublicPageService pages = mock(PublicPageService.class);
        when(pages.profilePage("ghost", false)).thenReturn(new PublicPage(false, "<html>missing</html>"));
        PublicProfileController controller = new PublicProfileController(mock(PublicProfileService.class), pages);

        ResponseEntity<String> response = controller.getProfilePage("ghost", "en-US");

        verify(pages).profilePage("ghost", false);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getBody()).isEqualTo("<html>missing</html>");
    }
}
