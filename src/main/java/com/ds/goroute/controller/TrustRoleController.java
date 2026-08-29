package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.ApplyTrustRoleRequest;
import com.ds.goroute.dto.response.TrustRoleResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.TrustRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Applying for a community role, and reading the ones somebody holds (TRUST-02). */
@RestController
@RequestMapping("/v1/api/trust-roles")
@RequiredArgsConstructor
public class TrustRoleController extends BaseService {

    private final TrustRoleService trustRoleService;

    @PostMapping("/applications")
    public ResponseEntity<BaseResponse<TrustRoleResponse>> apply(
            @Valid @RequestBody ApplyTrustRoleRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(trustRoleService.apply(userId, request)));
    }

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<List<TrustRoleResponse>>> mine(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trustRoleService.mine(userId)));
    }

    /** The badges another person holds, for their public profile. */
    @GetMapping("/users/{userId}")
    public ResponseEntity<BaseResponse<List<TrustRoleResponse>>> forUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trustRoleService.approvedFor(userId)));
    }
}
