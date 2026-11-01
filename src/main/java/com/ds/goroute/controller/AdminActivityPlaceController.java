package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AssignActivityPlaceRequest;
import com.ds.goroute.dto.response.AdminUnlinkedActivityResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.ActivityPlaceAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Trip activities whose address names a spot the catalogue does not have. Linking one is the
 * same act as approving an import mapping, so it sits under the same {@code places} permission.
 */
@RestController
@RequestMapping("/v1/api/admin/activities")
@RequiredArgsConstructor
@Validated
public class AdminActivityPlaceController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CATEGORY_FILTERS = 20;
    /** Activity categories are free lowercase codes (`hotel`, `transport`, `restaurant`…), not an enum. */
    private static final String CATEGORY_PATTERN = "[A-Za-z_]{1,40}";

    private final ActivityPlaceAdminService service;

    @GetMapping("/unlinked")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity<BaseResponse<PageResponse<AdminUnlinkedActivityResponse>>> listUnlinked(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean dismissed,
            @RequestParam(name = "category", required = false)
            @Size(max = MAX_CATEGORY_FILTERS) List<@Pattern(regexp = CATEGORY_PATTERN) String> categories,
            @RequestParam(name = "excludeCategory", required = false)
            @Size(max = MAX_CATEGORY_FILTERS) List<@Pattern(regexp = CATEGORY_PATTERN) String> excludedCategories,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<AdminUnlinkedActivityResponse> items =
                service.listUnlinked(search, dismissed, categories, excludedCategories, page, size);
        long total = service.countUnlinked(search, dismissed, categories, excludedCategories);
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(items, total, page, size)));
    }

    /** Takes the activity out of the queue when there is nothing to link it to. */
    @PostMapping("/{activityId}/dismiss")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity<BaseResponse<Void>> dismiss(@PathVariable UUID activityId, @CurrentUser UUID adminId) {
        service.dismiss(adminId, activityId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** Puts a dismissed activity back in the queue. */
    @DeleteMapping("/{activityId}/dismiss")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity<BaseResponse<Void>> restore(@PathVariable UUID activityId, @CurrentUser UUID adminId) {
        service.restore(adminId, activityId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** Links the activity to the catalogue place its address describes. */
    @PostMapping("/{activityId}/place")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity<BaseResponse<Void>> assignPlace(
            @PathVariable UUID activityId,
            @Valid @RequestBody AssignActivityPlaceRequest request,
            @CurrentUser UUID adminId) {
        service.assignPlace(adminId, activityId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
