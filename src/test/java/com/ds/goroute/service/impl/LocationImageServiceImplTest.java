package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateLocationImageRequest;
import com.ds.goroute.dto.response.LocationImageResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.enums.LocationDescriptionType;
import com.ds.goroute.enums.LocationImageKind;
import com.ds.goroute.enums.LocationImageKindFilter;
import com.ds.goroute.exception.BusinessException;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void listIsServedFromCacheWithoutDescriptionUntilAnAreaChanges() {
        UUID areaId = UUID.randomUUID();
        when(locationImageRepository.findAllForList()).thenReturn(List.of(LocationImage.builder()
            .id(areaId)
            .fullAddress("Đà Nẵng")
            .imageUrl("https://example.com/danang.jpg")
            .build()));
        when(locationImageRepository.findAllWardCodes()).thenReturn(Map.of(areaId, List.of("20194")));

        List<LocationImageResponse> first = service.getAllLocationImages(false);
        List<LocationImageResponse> second = service.getAllLocationImages(false);

        assertThat(first).singleElement().satisfies(area -> {
            assertThat(area.getDescription()).isNull();
            assertThat(area.getWardCodes()).containsExactly("20194");
        });
        assertThat(second).hasSize(1);
        verify(locationImageRepository, times(1)).findAllForList();
        verify(locationImageRepository, times(1)).findAllWardCodes();

        service.createLocationImage(CreateLocationImageRequest.builder()
            .fullAddress("Hội An")
            .imageUrl("https://example.com/hoian.jpg")
            .build());
        service.getAllLocationImages(false);

        verify(locationImageRepository, times(2)).findAllForList();
    }

    @Test
    void catalogueKeepsCountriesOutUnlessAskedAndNamesThemInTheRequestLanguage() {
        UUID vietnamId = UUID.randomUUID();
        UUID hanoiId = UUID.randomUUID();
        when(locationImageRepository.findAllForList()).thenReturn(List.of(
            LocationImage.builder().id(vietnamId).kind(LocationImageKind.COUNTRY).countryCode("VN")
                .fullAddress("Vietnam").imageUrl("https://flagcdn.com/w640/vn.png").build(),
            LocationImage.builder().id(hanoiId).kind(LocationImageKind.REGION).countryCode("VN")
                .parentId(vietnamId).fullAddress("Hà Nội").imageUrl("https://example.com/hanoi.jpg").build(),
            LocationImage.builder().id(UUID.randomUUID()).kind(LocationImageKind.COUNTRY).countryCode("JP")
                .fullAddress("Japan").imageUrl("https://flagcdn.com/w640/jp.png").build()));
        when(locationImageRepository.findAllNameTranslations())
            .thenReturn(Map.of(hanoiId, Map.of("en", "Hanoi", "ja", "ハノイ")));

        assertThat(service.getAllLocationImages(false))
            .as("what an app from before countries existed receives")
            .extracting(LocationImageResponse::getFullAddress)
            .containsExactly("Hà Nội");

        List<LocationImageResponse> all = service.getCatalogue(
            LocationImageKindFilter.ALL, null, Locale.forLanguageTag("vi"), false);
        assertThat(all).extracting(LocationImageResponse::getName)
            .containsExactly("Việt Nam", "Hà Nội", "Nhật Bản");
        assertThat(all.get(1).getCountryName()).isEqualTo("Việt Nam");
        assertThat(all.get(1).getParentId()).isEqualTo(vietnamId);

        assertThat(service.getCatalogue(LocationImageKindFilter.REGION, "vn", Locale.JAPANESE, false))
            .singleElement()
            .satisfies(region -> {
                assertThat(region.getName()).isEqualTo("ハノイ");
                assertThat(region.getFullAddress()).isEqualTo("Hà Nội");
            });
        assertThat(service.getCatalogue(LocationImageKindFilter.COUNTRY, "JP", Locale.ENGLISH, false))
            .extracting(LocationImageResponse::getName)
            .containsExactly("Japan");
        assertThat(service.getCatalogue(LocationImageKindFilter.REGION, "JP", Locale.ENGLISH, false)).isEmpty();
    }

    @Test
    void aNewRegionHangsUnderItsCountryAndTakesItsCurrency() {
        UUID japanId = UUID.randomUUID();
        when(locationImageRepository.findCountryId("JP")).thenReturn(Optional.of(japanId));
        when(locationImageRepository.findById(japanId)).thenReturn(Optional.of(LocationImage.builder()
            .id(japanId).kind(LocationImageKind.COUNTRY).countryCode("JP").currencyCode("JPY")
            .fullAddress("Japan").imageUrl("https://flagcdn.com/w640/jp.png").build()));

        LocationImageResponse kyoto = service.createLocationImage(CreateLocationImageRequest.builder()
            .fullAddress("Kyoto")
            .imageUrl("https://example.com/kyoto.jpg")
            .countryCode("jp")
            .nameTranslations(Map.of("vi", "Kyoto", "ja", "京都", "ko", " "))
            .priority(10)
            .build());

        assertThat(kyoto.getKind()).isEqualTo(LocationImageKind.REGION);
        assertThat(kyoto.getCountryCode()).isEqualTo("JP");
        assertThat(kyoto.getParentId()).isEqualTo(japanId);
        assertThat(kyoto.getCurrencyCode()).isEqualTo("JPY");
        assertThat(kyoto.getNameTranslations()).containsOnlyKeys("vi", "ja");
    }

    @Test
    void aSecondRowForACountryAndAnUnknownCodeAreRefused() {
        UUID japanId = UUID.randomUUID();
        when(locationImageRepository.findCountryId("JP")).thenReturn(Optional.of(japanId));
        when(locationImageRepository.findById(japanId)).thenReturn(Optional.of(LocationImage.builder()
            .id(japanId).kind(LocationImageKind.COUNTRY).countryCode("JP").fullAddress("Japan").build()));

        assertThatThrownBy(() -> service.createLocationImage(CreateLocationImageRequest.builder()
                .fullAddress("Nippon").imageUrl("https://example.com/jp.jpg")
                .kind(LocationImageKind.COUNTRY).countryCode("JP").priority(0).build()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("already exists");
        assertThatThrownBy(() -> service.createLocationImage(CreateLocationImageRequest.builder()
                .fullAddress("Atlantis").imageUrl("https://example.com/x.jpg")
                .countryCode("ZZ").priority(0).build()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Unknown country code");
    }

    @Test
    void destinationImageIsLookedUpOncePerNormalizedDestination() {
        when(locationImageRepository.findBestMatch("ha noi")).thenReturn(Optional.of(LocationImage.builder()
            .imageUrl("https://example.com/hanoi.jpg")
            .build()));

        String first = service.getImageForDestination("Hà Nội");
        String second = service.getImageForDestination("  ha   NOI ");

        assertThat(first).isEqualTo("https://example.com/hanoi.jpg");
        assertThat(second).isEqualTo(first);
        verify(locationImageRepository, times(1)).findBestMatch(anyString());
    }

    @Test
    void unmatchedDestinationFallsBackToTheDefaultAndIsNotLookedUpAgain() {
        when(locationImageRepository.findBestMatch("nowhere")).thenReturn(Optional.empty());

        String first = service.getImageForDestination("Nowhere");
        String second = service.getImageForDestination("nowhere");

        assertThat(first).startsWith("https://");
        assertThat(second).isEqualTo(first);
        verify(locationImageRepository, times(1)).findBestMatch(anyString());
    }
}
