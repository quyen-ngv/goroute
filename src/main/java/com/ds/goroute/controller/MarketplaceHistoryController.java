package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MarketplaceEntityVersionResponse;
import com.ds.goroute.service.MarketplaceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/marketplace-history")
@RequiredArgsConstructor
public class MarketplaceHistoryController {
    private final MarketplaceHistoryService service;

    @GetMapping("/{entityType}/{entityId}")
    public ResponseEntity<BaseResponse<List<MarketplaceEntityVersionResponse>>> list(
            @PathVariable String entityType, @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.list(entityType, entityId, page, size)));
    }
}
