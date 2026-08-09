package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MarketplaceEntityVersionResponse;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.MarketplaceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/marketplace-history")
@RequiredArgsConstructor
public class MarketplaceHistoryController {
    private final MarketplaceHistoryService service;

    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-history','get')")
    public ResponseEntity<BaseResponse<List<MarketplaceEntityVersionResponse>>> list(
            @PathVariable String entityType, @PathVariable UUID entityId,
            Authentication authentication, @RequestParam String reason,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        if (reason.isBlank()) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "A reason is required to access marketplace history");
        }
        service.audit(null, entityType, entityId, "ADMIN_HISTORY_ACCESSED",
                UUID.fromString(authentication.getName()), "ADMIN", reason, java.util.Map.of("page", page, "size", size));
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.list(entityType, entityId, page, size)));
    }
}
