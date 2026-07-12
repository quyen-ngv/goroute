package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.ImageStorageCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/images/cleanup")
@RequiredArgsConstructor
public class ImageCleanupController extends BaseService {

    private final ImageStorageCleanupService cleanupService;

    @GetMapping("/entities")
    public ResponseEntity<BaseResponse<List<String>>> supportedEntities() {
        return ResponseEntity.ok(ofSucceeded(cleanupService.supportedEntities()));
    }

    @PostMapping("/orphaned")
    public ResponseEntity<BaseResponse<ImageStorageCleanupService.OrphanImageCleanupResult>> cleanupOrphaned(
            @RequestBody OrphanCleanupRequest request
    ) {
        if (request == null) {
            request = new OrphanCleanupRequest();
        }
        boolean dryRun = request.dryRun == null || request.dryRun;
        int limit = request.limit == null ? 500 : Math.max(1, Math.min(request.limit, 5000));
        var result = cleanupService.cleanupOrphanedImages(
                request.entities,
                request.prefixes,
                dryRun,
                limit,
                Boolean.TRUE.equals(request.backupBeforeDelete),
                request.backupPrefix
        );
        return ResponseEntity.ok(ofSucceeded(result));
    }

    @DeleteMapping("/{entity}/{id}/images")
    public ResponseEntity<BaseResponse<ImageStorageCleanupService.DeleteRecordImagesResult>> deleteRecordImages(
            @PathVariable String entity,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ofSucceeded(cleanupService.deleteImagesForEntityRecord(entity, id)));
    }

    public static class OrphanCleanupRequest {
        public List<String> entities;
        public List<String> prefixes;
        public Boolean dryRun;
        public Integer limit;
        public Boolean backupBeforeDelete;
        public String backupPrefix;
    }
}
