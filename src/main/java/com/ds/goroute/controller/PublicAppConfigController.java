package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.AppConfigResponse;
import com.ds.goroute.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/api/public/configs")
@RequiredArgsConstructor
public class PublicAppConfigController {
    private final AppConfigService service;

    @GetMapping
    public ResponseEntity<BaseResponse<List<AppConfigResponse>>> list() {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listPublic()));
    }
}
