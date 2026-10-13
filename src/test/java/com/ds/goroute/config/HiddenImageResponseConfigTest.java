package com.ds.goroute.config;

import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.repository.ModerationFlagRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.impl.HiddenImageServiceImpl;
import com.ds.goroute.type.ModerationCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiddenImageResponseConfigTest {

    private static final String HIDDEN_KEY = "expenses/u1/3f1c2a8e-9b7d-4c5e-8a21-6d0f4b9e7c13.jpg";
    private static final String HIDDEN_URL = "https://cdn.example.com/" + HIDDEN_KEY;
    private static final String VISIBLE_URL = "https://cdn.example.com/expenses/u1/0a9b8c7d-6e5f-4a3b-2c1d-0e9f8a7b6c5d.jpg";
    private static final String PLACEHOLDER = "https://cdn.example.com/system/moderation/image-hidden.png";

    private final ModerationAuditRepository auditRepository = mock(ModerationAuditRepository.class);
    private final ModerationFlagRepository flagRepository = mock(ModerationFlagRepository.class);
    private final StorageService storageService = mock(StorageService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final HiddenImageServiceImpl hiddenImages = new HiddenImageServiceImpl(
            auditRepository, flagRepository, storageService, notificationService);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void hiddenUrlsAreReplacedInFieldsListsAndMapsButOthersAreUntouched() throws Exception {
        hide();
        onRequest("/v1/api/checkins/feed");

        String json = writer().writeValueAsString(new Photo("cover", HIDDEN_URL,
                List.of(VISIBLE_URL, HIDDEN_URL + "?w=400"), Map.of("avatar", HIDDEN_URL)));

        assertThat(json).doesNotContain(HIDDEN_KEY).contains(VISIBLE_URL).contains("\"title\":\"cover\"");
        assertThat(json.split(PLACEHOLDER, -1)).hasSize(4);
    }

    @Test
    void adminEndpointsStillSeeTheRealImage() throws Exception {
        hide();
        onRequest("/v1/api/admin/moderation/queue");

        String json = writer().writeValueAsString(Map.of("url", HIDDEN_URL));

        assertThat(json).contains(HIDDEN_URL);
    }

    @Test
    void hidingAnImageRecordsATakedownAndTellsTheUploader() {
        UUID ownerId = UUID.randomUUID();
        ImageModerationResult image = ImageModerationResult.builder()
                .id(UUID.randomUUID()).objectKey(HIDDEN_KEY).userId(ownerId).build();

        hiddenImages.hide(image, ModerationCategory.SEXUAL);

        verify(flagRepository).insertTakedown(any());
        verify(notificationService).createNotification(any(), any(), any(), anyString(), anyString(), any(), any());
        assertThat(hiddenImages.isHidden(HIDDEN_URL)).isTrue();
        assertThat(hiddenImages.isHidden(VISIBLE_URL)).isFalse();
    }

    @Test
    void reloadRebuildsTheSetFromActiveTakedowns() {
        hide();
        when(auditRepository.findHiddenImageKeys()).thenReturn(List.of());

        hiddenImages.reload();

        assertThat(hiddenImages.isHidden(HIDDEN_URL)).isFalse();
    }

    private void hide() {
        when(auditRepository.findHiddenImageKeys()).thenReturn(List.of(HIDDEN_KEY));
        when(storageService.readObject(anyString())).thenReturn(new byte[]{1});
        when(storageService.urlFor(anyString())).thenReturn(PLACEHOLDER);
        hiddenImages.reload();
    }

    private void onRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ObjectMapper writer() {
        @SuppressWarnings("unchecked")
        ObjectProvider<com.ds.goroute.service.HiddenImageService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(hiddenImages);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(new ObjectMapper());
        List<HttpMessageConverter<?>> converters = new ArrayList<>(List.of(converter));
        new HiddenImageResponseConfig(provider).extendMessageConverters(converters);
        return converter.getObjectMapper();
    }

    record Photo(String title, String url, List<String> gallery, Map<String, String> owner) {
    }
}
