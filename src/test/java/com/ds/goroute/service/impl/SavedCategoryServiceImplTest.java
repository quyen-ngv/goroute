package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdateSavedCategoryRequest;
import com.ds.goroute.dto.response.SavedCategoryDetailResponse;
import com.ds.goroute.dto.response.SavedCategoryResponse;
import com.ds.goroute.entity.SavedCategory;
import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.SavedCategoryMapper;
import com.ds.goroute.mapper.SavedPlaceMapper;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.type.SavedCategoryVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedCategoryServiceImplTest {

    private final SavedCategoryMapper categoryMapper = mock(SavedCategoryMapper.class);
    private final SavedPlaceMapper savedPlaceMapper = mock(SavedPlaceMapper.class);
    private final UserBlockService userBlockService = mock(UserBlockService.class);
    private final StorageService storageService = mock(StorageService.class);
    private final ImageStorageCleanupService cleanup = mock(ImageStorageCleanupService.class);
    private final SavedCategoryServiceImpl service = new SavedCategoryServiceImpl(
            categoryMapper, savedPlaceMapper, userBlockService, storageService, cleanup);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID visitorId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Nested
    class Reading {

        @Test
        void visitorSeesOnlyPublicCategoriesThatHoldSomething() {
            when(categoryMapper.findSummariesByUser(ownerId, true, SavedCategoryServiceImpl.MAX_LISTED_CATEGORIES))
                    .thenReturn(List.of(category("Ăn uống", SavedCategoryVisibility.PUBLIC, 3),
                            category("Trống", SavedCategoryVisibility.PUBLIC, 0)));

            List<SavedCategoryResponse> categories = service.listPublic(ownerId, visitorId);

            assertThat(categories).extracting(SavedCategoryResponse::getName).containsExactly("Ăn uống");
        }

        @Test
        void blockedVisitorSeesNoCategories() {
            when(userBlockService.blockedBetween(ownerId, visitorId)).thenReturn(true);

            assertThat(service.listPublic(ownerId, visitorId)).isEmpty();
            verify(categoryMapper, never()).findSummariesByUser(any(), eq(true), anyInt());
        }

        @Test
        void privateCategoryIsNotFoundForAVisitor() {
            when(categoryMapper.findSummaryById(categoryId))
                    .thenReturn(category("Riêng", SavedCategoryVisibility.PRIVATE, 2));

            assertThatThrownBy(() -> service.get(visitorId, categoryId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getError().getCode()).isEqualTo(ErrorConstant.NOT_FOUND));
        }

        @Test
        void publicCategoryShowsItsItemsWithoutTheOwnersTags() {
            when(categoryMapper.findSummaryById(categoryId))
                    .thenReturn(category("Ăn uống", SavedCategoryVisibility.PUBLIC, 1));
            when(savedPlaceMapper.findByCategoryKey(ownerId, "Ăn uống", SavedCategoryServiceImpl.MAX_LISTED_ITEMS))
                    .thenReturn(List.of(item()));

            SavedCategoryDetailResponse detail = service.get(visitorId, categoryId);

            assertThat(detail.isOwner()).isFalse();
            assertThat(detail.getItems()).singleElement()
                    .satisfies(item -> assertThat(item.getTags()).isNull());
        }

        @Test
        void ownerSeesTheirPrivateCategoryWithTags() {
            when(categoryMapper.findSummaryById(categoryId))
                    .thenReturn(category("Riêng", SavedCategoryVisibility.PRIVATE, 1));
            when(savedPlaceMapper.findByCategoryKey(ownerId, "Riêng", SavedCategoryServiceImpl.MAX_LISTED_ITEMS))
                    .thenReturn(List.of(item()));

            SavedCategoryDetailResponse detail = service.get(ownerId, categoryId);

            assertThat(detail.isOwner()).isTrue();
            assertThat(detail.getItems()).singleElement()
                    .satisfies(item -> assertThat(item.getTags()).containsExactly("hẹn hò"));
        }
    }

    @Nested
    class Updating {

        @BeforeEach
        void ownedCategory() {
            when(categoryMapper.findById(categoryId))
                    .thenReturn(category("Ăn uống", SavedCategoryVisibility.PRIVATE, 2));
            when(categoryMapper.update(any())).thenReturn(1);
            when(categoryMapper.findSummaryById(categoryId))
                    .thenReturn(category("Quán ngon", SavedCategoryVisibility.PUBLIC, 2));
        }

        @Test
        void renameMovesEveryItemAfterCollapsingDuplicates() {
            service.update(ownerId, categoryId, request("  Quán ngon ", null, SavedCategoryVisibility.PUBLIC));

            var order = inOrder(savedPlaceMapper, categoryMapper);
            order.verify(savedPlaceMapper).deleteDuplicatesInCategory(ownerId, "Ăn uống");
            order.verify(savedPlaceMapper).renameCategory(ownerId, "Ăn uống", "Quán ngon");
            order.verify(categoryMapper).update(any());
        }

        @Test
        void renameOntoAnotherCategoryIsAConflict() {
            when(categoryMapper.findByUserAndName(ownerId, "Cà phê"))
                    .thenReturn(category("Cà phê", SavedCategoryVisibility.PRIVATE, 1));

            assertThatThrownBy(() -> service.update(ownerId, categoryId,
                    request("Cà phê", null, SavedCategoryVisibility.PRIVATE)))
                    .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getError().getCode())
                            .isEqualTo(ErrorConstant.SAVED_CATEGORY_NAME_TAKEN));
            verify(savedPlaceMapper, never()).renameCategory(any(), anyString(), anyString());
        }

        @Test
        void changingOnlyTheCaseIsNotAConflict() {
            service.update(ownerId, categoryId, request("ĂN UỐNG", null, SavedCategoryVisibility.PRIVATE));

            verify(categoryMapper, never()).findByUserAndName(any(), any());
            verify(savedPlaceMapper).renameCategory(ownerId, "Ăn uống", "ĂN UỐNG");
        }

        @Test
        void sameNameTouchesNoItems() {
            service.update(ownerId, categoryId, request("Ăn uống", null, SavedCategoryVisibility.PUBLIC));

            verify(savedPlaceMapper, never()).renameCategory(any(), anyString(), anyString());
            verify(cleanup, never()).deleteImagesForEntityRecord(anyString(), any(), any());
        }

        @Test
        void coverMustBeTheUsersOwnUpload() {
            when(storageService.extractObjectKey("https://cdn/places/x.jpg")).thenReturn("places/x.jpg");

            assertThatThrownBy(() -> service.update(ownerId, categoryId,
                    request("Ăn uống", "https://cdn/places/x.jpg", SavedCategoryVisibility.PRIVATE)))
                    .isInstanceOf(BusinessException.class);
            verify(categoryMapper, never()).update(any());
        }

        @Test
        void newCoverReleasesTheOldOneButKeepsTheNew() {
            String cover = "https://cdn/expenses/" + ownerId + "/a.jpg";
            when(storageService.extractObjectKey(cover)).thenReturn("expenses/" + ownerId + "/a.jpg");

            service.update(ownerId, categoryId, request("Ăn uống", cover, SavedCategoryVisibility.PRIVATE));

            verify(cleanup).deleteImagesForEntityRecord(SavedCategoryServiceImpl.COVER_ENTITY, categoryId, List.of(cover));
        }

        @Test
        void someoneElsesCategoryIsNotFound() {
            assertThatThrownBy(() -> service.update(visitorId, categoryId,
                    request("X", null, SavedCategoryVisibility.PRIVATE)))
                    .isInstanceOf(BusinessException.class);
            verify(categoryMapper, never()).update(any());
        }
    }

    @Nested
    class Deleting {

        @Test
        void deletesTheCoverTheItemsAndTheCategory() {
            when(categoryMapper.findById(categoryId))
                    .thenReturn(category("Ăn uống", SavedCategoryVisibility.PUBLIC, 2));
            when(categoryMapper.deleteOwned(categoryId, ownerId)).thenReturn(1);

            service.delete(ownerId, categoryId);

            var order = inOrder(cleanup, savedPlaceMapper, categoryMapper);
            order.verify(cleanup).deleteImagesForEntityRecord(SavedCategoryServiceImpl.COVER_ENTITY, categoryId);
            order.verify(savedPlaceMapper).deleteByCategoryKey(ownerId, "Ăn uống");
            order.verify(categoryMapper).deleteOwned(categoryId, ownerId);
        }

        @Test
        void someoneElsesCategoryIsLeftAlone() {
            when(categoryMapper.findById(categoryId))
                    .thenReturn(category("Ăn uống", SavedCategoryVisibility.PUBLIC, 2));

            assertThatThrownBy(() -> service.delete(visitorId, categoryId)).isInstanceOf(BusinessException.class);
            verify(savedPlaceMapper, never()).deleteByCategoryKey(any(), any());
        }
    }

    private SavedCategory category(String name, SavedCategoryVisibility visibility, int itemCount) {
        return SavedCategory.builder()
                .id(categoryId)
                .userId(ownerId)
                .name(name)
                .visibility(visibility)
                .itemCount(itemCount)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SavedPlace item() {
        return SavedPlace.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .placeId(UUID.randomUUID().toString())
                .itemType("PLACE")
                .name("Phở Thìn")
                .category("Ăn uống")
                .tags(new String[]{"hẹn hò"})
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UpdateSavedCategoryRequest request(String name, String coverUrl, SavedCategoryVisibility visibility) {
        return UpdateSavedCategoryRequest.builder().name(name).coverUrl(coverUrl).visibility(visibility).build();
    }
}
