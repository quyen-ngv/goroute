package com.ds.goroute.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class AssignRolesRequest {
    @NotEmpty(message = "Role codes cannot be empty")
    private List<String> roleCodes;
}
