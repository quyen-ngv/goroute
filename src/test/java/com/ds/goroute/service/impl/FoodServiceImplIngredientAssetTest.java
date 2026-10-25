package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateFoodRequest;
import com.ds.goroute.dto.response.FoodCoreIngredientDto;
import com.ds.goroute.dto.response.FoodDetailResponse;
import com.ds.goroute.entity.Food;
import com.ds.goroute.entity.FoodWithScoreRow;
import com.ds.goroute.entity.LibraryAsset;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.LibraryAssetRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Food ingredients that reference the shared asset library (epic 06 §2.5b). */
class FoodServiceImplIngredientAssetTest {

    private static final String ICON_V2 = "https://cdn.example/asset-library/ingredient/lemongrass-v2.webp";

    private final FoodRepository foodRepository = mock(FoodRepository.class);
    private final LibraryAssetRepository libraryAssetRepository = mock(LibraryAssetRepository.class);
    private FoodServiceImpl service;

    private final UUID assetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FoodServiceImpl(foodRepository, mock(PlaceRepository.class), mock(ExchangeRateService.class),
                mock(ImageStorageCleanupService.class), mock(PlaceSearchIndexService.class), libraryAssetRepository);
    }

    @Test
    @DisplayName("A linked ingredient is stored with its asset id and without a copied image URL")
    void storesAssetIdWithoutImage() {
        when(libraryAssetRepository.findByIds(any())).thenReturn(Map.of(assetId, lemongrass()));
        CreateFoodRequest request = createRequest(List.of(
                FoodCoreIngredientDto.builder().assetId(assetId).image("https://old/copied.jpg")
                        .name("Lemongrass").description("the broth's aroma").build(),
                FoodCoreIngredientDto.builder().image("https://cdn.example/x.jpg").name("Chili").build()));

        service.adminCreate(request);

        ArgumentCaptor<Food> saved = ArgumentCaptor.forClass(Food.class);
        verify(foodRepository).insertFood(saved.capture());
        List<FoodCoreIngredientDto> stored = JsonUtils.fromJson(saved.getValue().getCoreIngredients(),
                new TypeReference<List<FoodCoreIngredientDto>>() {});
        assertThat(stored.get(0).getAssetId()).isEqualTo(assetId);
        assertThat(stored.get(0).getImage()).isNull();
        assertThat(stored.get(0).getDescription()).isEqualTo("the broth's aroma");
        assertThat(stored.get(1).getImage()).isEqualTo("https://cdn.example/x.jpg");
    }

    @Test
    @DisplayName("An asset id that is not an ingredient in the library is refused")
    void rejectsUnknownAsset() {
        when(libraryAssetRepository.findByIds(any())).thenReturn(Map.of());
        CreateFoodRequest request = createRequest(List.of(
                FoodCoreIngredientDto.builder().assetId(assetId).name("Lemongrass").build()));

        assertThatThrownBy(() -> service.adminCreate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(assetId.toString());
        verify(foodRepository, never()).insertFood(any());
    }

    @Test
    @DisplayName("The app gets the asset's current image and its name in the request language")
    void detailResolvesAsset() {
        UUID foodId = UUID.randomUUID();
        String storedIngredients = JsonUtils.toJson(List.of(
                FoodCoreIngredientDto.builder().assetId(assetId).name("Lemongrass ").description("aroma").build()));
        FoodWithScoreRow row = FoodWithScoreRow.builder()
                .id(foodId).nameEn("Bun Bo Hue").description("d").category("NOODLE")
                .coreIngredients(storedIngredients).score(90).build();
        when(foodRepository.findFoodDetailByCity(eq(foodId), anyString())).thenReturn(Optional.of(row));
        when(foodRepository.findFoodById(foodId)).thenReturn(Optional.of(Food.builder().id(foodId).build()));
        when(foodRepository.findCityScoresByFoodId(foodId)).thenReturn(List.of());
        when(libraryAssetRepository.findByIds(any())).thenReturn(Map.of(assetId, lemongrass()));

        FoodDetailResponse detail = service.getDetail(foodId, "hue");

        assertThat(detail.getCoreIngredients()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getImage()).isEqualTo(ICON_V2);
                    assertThat(item.getName()).isEqualTo("Lemongrass");
                    assertThat(item.getDescription()).isEqualTo("aroma");
                });
    }

    private LibraryAsset lemongrass() {
        return LibraryAsset.builder().id(assetId).collection("INGREDIENT").assetKey("lemongrass")
                .nameEn("Lemongrass").nameVi("Sả").imageUrl(ICON_V2).status("ACTIVE").version(2).build();
    }

    private static CreateFoodRequest createRequest(List<FoodCoreIngredientDto> ingredients) {
        CreateFoodRequest request = new CreateFoodRequest();
        request.setNameVi("Bún bò Huế");
        request.setNameEn("Bun Bo Hue");
        request.setDescription("d");
        request.setCategory("NOODLE");
        request.setCoreIngredients(ingredients);
        return request;
    }
}
