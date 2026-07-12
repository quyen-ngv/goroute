package com.ds.goroute.controller;

import com.ds.goroute.dto.request.ModeratePlaceImportMappingRequest;
import com.ds.goroute.dto.response.AdminPlaceImportMappingResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.PlaceImportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/place-import-mappings")
@RequiredArgsConstructor
@Tag(name = "Admin Place Import Mappings", description = "Moderate imported place mappings before assigning them to user data")
public class AdminPlaceImportMappingController extends BaseService {

    private final PlaceImportJobService placeImportJobService;

    @GetMapping
    @Operation(summary = "List place import mapping history")
    public ResponseEntity list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<AdminPlaceImportMappingResponse> response =
                placeImportJobService.adminListMappings(status, page, size);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/{itemId}/approve")
    @Operation(summary = "Approve a mapping and apply it to the activity or social saved item")
    public ResponseEntity approve(
            @PathVariable UUID itemId,
            @RequestBody(required = false) ModeratePlaceImportMappingRequest request) {
        AdminPlaceImportMappingResponse response = placeImportJobService.adminApproveMapping(
                itemId, request == null ? null : request.getNote());
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/{itemId}/reject")
    @Operation(summary = "Reject a mapping without changing user activity or saved social item")
    public ResponseEntity reject(
            @PathVariable UUID itemId,
            @RequestBody(required = false) ModeratePlaceImportMappingRequest request) {
        placeImportJobService.adminRejectMapping(itemId, request == null ? null : request.getNote());
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
