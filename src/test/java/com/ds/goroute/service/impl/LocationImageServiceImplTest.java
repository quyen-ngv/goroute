package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateLocationImageRequest;
import com.ds.goroute.dto.response.LocationImageResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.enums.LocationDescriptionType;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LocationImageServiceImplTest {

    @Mock
    private LocationImageRepository locationImageRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ImageStorageCleanupService imageStorageCleanupService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private LocationImageServiceImpl service;

    @Test
    void createGeneratesAllDescriptionSectionsWithNullContent() throws Exception {
        CreateLocationImageRequest request = CreateLocationImageRequest.builder()
            .fullAddress("Đà Nẵng")
            .slogan("Thành phố đáng sống")
            .imageUrl("https://example.com/danang.jpg")
            .priority(50)
            .build();

        LocationImageResponse response = service.createLocationImage(request);

        assertThat(response.getDescription())
            .extracting(section -> section.getType())
            .containsExactly(LocationDescriptionType.values());
        assertThat(response.getDescription())
            .allSatisfy(section -> assertThat(section.getContent().getContent()).isNull());
        assertThat(response.getDescription().get(1).getContent().getTitle())
            .isEqualTo("Vibe ở đây thế nào");

        ArgumentCaptor<LocationImage> captor = ArgumentCaptor.forClass(LocationImage.class);
        verify(locationImageRepository).insert(captor.capture());
        assertThat(objectMapper.readTree(captor.getValue().getDescription()).isArray()).isTrue();
        assertThat(objectMapper.readTree(captor.getValue().getDescription()).size()).isEqualTo(6);
    }
}
