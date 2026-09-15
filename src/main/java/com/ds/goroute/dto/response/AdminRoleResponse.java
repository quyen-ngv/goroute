package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRoleResponse {
    private String code;
    private String name;
    private Integer permissionCount;
    private List<String> resources;
    private Map<String, List<String>> permissions; // resource -> [actions]
}
