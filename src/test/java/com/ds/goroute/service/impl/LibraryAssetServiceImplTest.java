package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateLibraryAssetRequest;
import com.ds.goroute.dto.request.UpdateLibraryAssetRequest;
import com.ds.goroute.dto.response.LibraryAssetResponse;
import com.ds.goroute.entity.FoodAssetUsageRow;
import com.ds.goroute.entity.LibraryAsset;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.LibraryAssetRepository;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.AssetCollection;
import com.ds.goroute.type.AssetStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryAssetServiceImplTest {

    private static final String OWN_URL = "https://cdn.example/asset-library/ingredient/a.webp";

    @Mock
    private LibraryAssetRepository libraryAssetRepository;
    @Mock
    private FoodRepository foodRepository;
    @Mock
    private FileUploadService fileUploadService;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private LibraryAssetServiceImpl service;

    private final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("Create stores an ACTIVE asset with version 1 when it comes with an own-storage image")
    void createStoresAsset() {
        when(libraryAssetRepository.findByCollectionAndKey("INGREDIENT", "lemongrass")).thenReturn(Optional.empty());
        when(storageService.extractObjectKey(OWN_URL)).thenReturn("asset-library/ingredient/a.webp");

        LibraryAssetResponse response = service.adminCreate(createRequest("lemongrass", OWN_URL), adminId);

        ArgumentCaptor<LibraryAsset> saved = ArgumentCaptor.forClass(LibraryAsset.class);
        verify(libraryAssetRepository).insert(saved.capture());
        assertThat(saved.getValue())
                .extracting(LibraryAsset::getCollection, LibraryAsset::getAssetKey, LibraryAsset::getStatus,
                        LibraryAsset::getVersion, LibraryAsset::getImageUrl)
                .containsExactly("INGREDIENT", "lemongrass", "ACTIVE", 1, OWN_URL);
        assertThat(response.getNames()).containsEntry("en", "Lemongrass").containsEntry("vi", "Sả");
    }

    @Test
    @DisplayName("Create without an image makes a placeholder at version 0")
    void createPlaceholder() {
        when(libraryAssetRepository.findByCollectionAndKey("INGREDIENT", "cao-lau-noodles")).thenReturn(Optional.empty());

        service.adminCreate(createRequest("cao-lau-noodles", null), adminId);

        ArgumentCaptor<LibraryAsset> saved = ArgumentCaptor.forClass(LibraryAsset.class);
        verify(libraryAssetRepository).insert(saved.capture());
        assertThat(saved.getValue().getImageUrl()).isNull();
        assertThat(saved.getValue().getVersion()).isZero();
    }

    @Test
    @DisplayName("Create refuses a key another asset of the collection has")
    void createRejectsTakenKey() {
        when(libraryAssetRepository.findByCollectionAndKey("INGREDIENT", "lemongrass"))
                .thenReturn(Optional.of(asset(UUID.randomUUID(), OWN_URL)));

        assertThatThrownBy(() -> service.adminCreate(createRequest("lemongrass", null), adminId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.ASSET_LIBRARY_KEY_TAKEN));
        verify(libraryAssetRepository, never()).insert(any());
    }

    @Test
    @DisplayName("An image URL outside our storage is refused, so external images must be uploaded")
    void createRejectsExternalImage() {
        when(libraryAssetRepository.findByCollectionAndKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(storageService.extractObjectKey("https://elsewhere.example/x.png")).thenReturn(null);

        assertThatThrownBy(() -> service.adminCreate(createRequest("lime", "https://elsewhere.example/x.png"), adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own storage");
        verify(libraryAssetRepository, never()).insert(any());
    }

    @Test
    @DisplayName("Replacing the image uploads under asset-library/<collection> and bumps the version")
    void replaceImageBumpsVersion() {
        UUID id = UUID.randomUUID();
        when(libraryAssetRepository.findById(id)).thenReturn(Optional.of(asset(id, OWN_URL)));
        when(fileUploadService.uploadImage(any(ImageUploadRequest.class), any()))
                .thenReturn(ImageUploadOutcome.accepted("b.png", "https://cdn.example/asset-library/ingredient/b.webp"));
        when(libraryAssetRepository.update(any())).thenReturn(1);

        LibraryAssetResponse response = service.adminReplaceImage(
                id, new MockMultipartFile("file", "b.png", "image/png", new byte[]{1}), adminId);

        ArgumentCaptor<ImageUploadRequest> upload = ArgumentCaptor.forClass(ImageUploadRequest.class);
        verify(fileUploadService).uploadImage(upload.capture(), any());
        assertThat(upload.getValue().objectPrefix()).isEqualTo("asset-library/ingredient");
        assertThat(upload.getValue().entryPoint()).isEqualTo(ImageUploadRequest.ImageEntryPoint.ASSET_LIBRARY);
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example/asset-library/ingredient/b.webp");
        assertThat(response.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("Archiving keeps the image and the version")
    void archiveKeepsImage() {
        UUID id = UUID.randomUUID();
        when(libraryAssetRepository.findById(id)).thenReturn(Optional.of(asset(id, OWN_URL)));
        when(libraryAssetRepository.update(any())).thenReturn(1);
        UpdateLibraryAssetRequest request = new UpdateLibraryAssetRequest();
        request.setStatus(AssetStatus.ARCHIVED);

        LibraryAssetResponse response = service.adminUpdate(id, request, adminId);

        assertThat(response.getStatus()).isEqualTo("ARCHIVED");
        assertThat(response.getImageUrl()).isEqualTo(OWN_URL);
        assertThat(response.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("An asset still used by a food cannot be deleted")
    void deleteRefusedWhileInUse() {
        UUID id = UUID.randomUUID();
        when(libraryAssetRepository.findById(id)).thenReturn(Optional.of(asset(id, OWN_URL)));
        when(foodRepository.findIngredientAssetUsages(List.of(id))).thenReturn(List.of(
                FoodAssetUsageRow.builder().assetId(id).foodId(UUID.randomUUID()).nameVi("Phở").nameEn("Pho").build()));

        assertThatThrownBy(() -> service.adminDelete(id))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.ASSET_LIBRARY_IN_USE));
        verify(libraryAssetRepository, never()).delete(eq(id));
    }

    @Test
    @DisplayName("Detail lists the foods that use the asset")
    void detailListsUsages() {
        UUID id = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();
        when(libraryAssetRepository.findById(id)).thenReturn(Optional.of(asset(id, OWN_URL)));
        when(foodRepository.findIngredientAssetUsages(List.of(id))).thenReturn(List.of(
                FoodAssetUsageRow.builder().assetId(id).foodId(foodId).nameVi("Phở").nameEn("Pho").build()));

        LibraryAssetResponse response = service.adminGet(id);

        assertThat(response.getUsageCount()).isEqualTo(1);
        assertThat(response.getUsedBy()).singleElement()
                .satisfies(usage -> {
                    assertThat(usage.getType()).isEqualTo("FOOD");
                    assertThat(usage.getId()).isEqualTo(foodId);
                    assertThat(usage.getName()).isEqualTo("Phở");
                });
    }

    private static CreateLibraryAssetRequest createRequest(String key, String imageUrl) {
        CreateLibraryAssetRequest request = new CreateLibraryAssetRequest();
        request.setCollection(AssetCollection.INGREDIENT);
        request.setKey(key);
        request.setNameEn("Lemongrass");
        request.setNameVi("Sả");
        request.setImageUrl(imageUrl);
        return request;
    }

    private static LibraryAsset asset(UUID id, String imageUrl) {
        return LibraryAsset.builder()
                .id(id)
                .collection("INGREDIENT")
                .assetKey("lemongrass")
                .nameEn("Lemongrass")
                .tags("[]")
                .imageUrl(imageUrl)
                .source("UNKNOWN")
                .status("ACTIVE")
                .version(1)
                .build();
    }
}
