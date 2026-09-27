package com.ds.goroute.quest.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.quest.dto.QuestArObjectAssetResponse;
import com.ds.goroute.quest.dto.QuestArObjectFileResponse;
import com.ds.goroute.quest.dto.SaveQuestArObjectAssetRequest;
import com.ds.goroute.quest.service.QuestArObjectLibraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The AR object library on the console (§3.15). The console manages the 3D objects here; the AR
 * objects placed on checkpoints are read-only on the console (they need a phone on site).
 */
@Validated
@RestController
@RequestMapping("/v1/api/admin/quest-ar-objects")
@RequiredArgsConstructor
public class QuestArObjectAdminController {

    private final QuestArObjectLibraryService library;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','get')")
    public ResponseEntity<BaseResponse<List<QuestArObjectAssetResponse>>> list() {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(library.list(false)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','get')")
    public ResponseEntity<BaseResponse<QuestArObjectAssetResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(library.get(id)));
    }

    /** Uploads one file: {@code kind} is GLB, USDZ or THUMBNAIL. A GLB is checked against the spec first. */
    @PostMapping(value = "/files", consumes = "multipart/form-data")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','update')")
    public ResponseEntity<BaseResponse<QuestArObjectFileResponse>> upload(
            @CurrentUser UUID userId,
            @RequestParam QuestArObjectLibraryService.FileKind kind,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(library.upload(userId, kind, file)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','update')")
    public ResponseEntity<BaseResponse<QuestArObjectAssetResponse>> create(
            @CurrentUser UUID userId,
            @Valid @RequestBody SaveQuestArObjectAssetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(library.create(userId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','update')")
    public ResponseEntity<BaseResponse<QuestArObjectAssetResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SaveQuestArObjectAssetRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(library.update(id, request)));
    }
}
