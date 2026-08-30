package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpsertPassportDefinitionRequest;
import com.ds.goroute.dto.request.UpsertPassportTagRequest;
import com.ds.goroute.dto.request.UpsertPassportRewardRequest;
import com.ds.goroute.dto.request.UpsertPassportStampRuleRequest;
import com.ds.goroute.dto.response.PassportDefinitionResponse;
import com.ds.goroute.dto.response.PassportProvinceOptionResponse;
import com.ds.goroute.dto.response.PassportTagResponse;
import com.ds.goroute.dto.response.PassportRewardResponse;
import com.ds.goroute.dto.response.PassportStampRuleResponse;
import com.ds.goroute.service.PassportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Operator CRUD for the data-driven passport/tag catalogue. */
@RestController
@RequestMapping("/v1/api/admin/passport-catalog")
@RequiredArgsConstructor
public class AdminPassportCatalogController extends BaseController {

    private final PassportService passportService;

    @GetMapping("/passports")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-catalog','get')")
    public ResponseEntity<BaseResponse<List<PassportDefinitionResponse>>> passports(
            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return ResponseEntity.ok(ofSucceeded(passportService.passportDefinitions(includeInactive)));
    }

    @GetMapping("/provinces")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-catalog','get')")
    public ResponseEntity<BaseResponse<List<PassportProvinceOptionResponse>>> provinces() {
        return ResponseEntity.ok(ofSucceeded(passportService.provinceOptions()));
    }

    @PostMapping("/passports")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-catalog','create')")
    public ResponseEntity<BaseResponse<PassportDefinitionResponse>> createPassport(
            @Valid @RequestBody UpsertPassportDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(passportService.createPassportDefinition(request)));
    }

    @PutMapping("/passports/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-catalog','update')")
    public ResponseEntity<BaseResponse<PassportDefinitionResponse>> updatePassport(
            @PathVariable UUID id, @Valid @RequestBody UpsertPassportDefinitionRequest request) {
        return ResponseEntity.ok(ofSucceeded(passportService.updatePassportDefinition(id, request)));
    }

    @GetMapping("/tags")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-catalog','get')")
    public ResponseEntity<BaseResponse<List<PassportTagResponse>>> tags(
            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return ResponseEntity.ok(ofSucceeded(passportService.passportTags(includeInactive)));
    }

    @PostMapping("/tags")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-catalog','create')")
    public ResponseEntity<BaseResponse<PassportTagResponse>> createTag(
            @Valid @RequestBody UpsertPassportTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(passportService.createPassportTag(request)));
    }

    @PutMapping("/tags/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-catalog','update')")
    public ResponseEntity<BaseResponse<PassportTagResponse>> updateTag(
            @PathVariable UUID id, @Valid @RequestBody UpsertPassportTagRequest request) {
        return ResponseEntity.ok(ofSucceeded(passportService.updatePassportTag(id, request)));
    }

    @GetMapping("/stamp-rules")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-rewards','get')")
    public ResponseEntity<BaseResponse<List<PassportStampRuleResponse>>> stampRules(
            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return ResponseEntity.ok(ofSucceeded(passportService.passportStampRules(includeInactive)));
    }

    @PostMapping("/stamp-rules")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-rewards','create')")
    public ResponseEntity<BaseResponse<PassportStampRuleResponse>> createStampRule(
            @Valid @RequestBody UpsertPassportStampRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(passportService.createPassportStampRule(request)));
    }

    @PutMapping("/stamp-rules/{code}/{version}")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-rewards','update')")
    public ResponseEntity<BaseResponse<PassportStampRuleResponse>> updateStampRule(
            @PathVariable String code, @PathVariable int version,
            @Valid @RequestBody UpsertPassportStampRuleRequest request) {
        return ResponseEntity.ok(ofSucceeded(passportService.updatePassportStampRule(code, version, request)));
    }

    @GetMapping("/rewards")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-rewards','get')")
    public ResponseEntity<BaseResponse<List<PassportRewardResponse>>> rewards(
            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return ResponseEntity.ok(ofSucceeded(passportService.passportRewards(includeInactive)));
    }

    @PostMapping("/rewards")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-rewards','create')")
    public ResponseEntity<BaseResponse<PassportRewardResponse>> createReward(
            @Valid @RequestBody UpsertPassportRewardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(passportService.createPassportReward(request)));
    }

    @PutMapping("/rewards/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-rewards','update')")
    public ResponseEntity<BaseResponse<PassportRewardResponse>> updateReward(
            @PathVariable UUID id, @Valid @RequestBody UpsertPassportRewardRequest request) {
        return ResponseEntity.ok(ofSucceeded(passportService.updatePassportReward(id, request)));
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("@adminAuthorization.can(authentication,'passport-catalog','create') || @adminAuthorization.can(authentication,'passport-catalog','update')")
    public ResponseEntity<BaseResponse<String>> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ofSucceeded(passportService.uploadCatalogImage(file)));
    }
}
