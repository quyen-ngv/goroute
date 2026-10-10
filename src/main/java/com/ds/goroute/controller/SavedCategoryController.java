package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpdateSavedCategoryRequest;
import com.ds.goroute.dto.response.SavedCategoryDetailResponse;
import com.ds.goroute.dto.response.SavedCategoryResponse;
import com.ds.goroute.service.SavedCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Saved categories as records: rename, cover, visibility, delete, and the public ones on a
 * profile. Every route needs a signed-in user. A category is created by saving an item into a
 * new name ({@code POST /v1/api/saved-places}); lists are capped at 500 and not paged.
 */
@RestController
@Validated
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class SavedCategoryController extends BaseController {

    private final SavedCategoryService savedCategoryService;

    @GetMapping("/saved-categories")
    public ResponseEntity<BaseResponse<List<SavedCategoryResponse>>> listMine(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(savedCategoryService.listMine(userId)));
    }

    /** The owner sees any of their categories; anyone else only a PUBLIC one (404 otherwise). */
    @GetMapping("/saved-categories/{categoryId}")
    public ResponseEntity<BaseResponse<SavedCategoryDetailResponse>> get(
            @PathVariable UUID categoryId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(savedCategoryService.get(userId, categoryId)));
    }

    @PutMapping("/saved-categories/{categoryId}")
    public ResponseEntity<BaseResponse<SavedCategoryResponse>> update(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateSavedCategoryRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(savedCategoryService.update(userId, categoryId, request)));
    }

    /** Deletes the category and every save in it. */
    @DeleteMapping("/saved-categories/{categoryId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID categoryId,
            @CurrentUser UUID userId) {
        savedCategoryService.delete(userId, categoryId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** Another user's PUBLIC, non-empty categories, for their profile. */
    @GetMapping("/users/{ownerId}/saved-categories")
    public ResponseEntity<BaseResponse<List<SavedCategoryResponse>>> listPublic(
            @PathVariable UUID ownerId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(savedCategoryService.listPublic(ownerId, userId)));
    }
}
