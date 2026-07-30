package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpsertAppConfigRequest;
import com.ds.goroute.dto.response.AppConfigResponse;

import java.util.List;
import java.util.UUID;

public interface AppConfigService {
    List<AppConfigResponse> listPublic();
    List<AppConfigResponse> adminList(String query, String label, Boolean active, int page, int size);
    AppConfigResponse adminGet(UUID id);
    AppConfigResponse adminCreate(UpsertAppConfigRequest request);
    AppConfigResponse adminUpdate(UUID id, UpsertAppConfigRequest request);
    void adminDelete(UUID id);
}
