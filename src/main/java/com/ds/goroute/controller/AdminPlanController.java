package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.utils.AdminListSort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.*;

@RestController
@RequestMapping("/v1/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {
    private final AdminMapper adminMapper;

    /** Columns the console may order the trip list by; anything else falls back to newest first. */
    private static final Set<String> PLAN_SORT_FIELDS = Set.of(
            "name", "destination", "username", "activity_count", "member_count", "created_at");

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'plans','get')")
    public BaseResponse<List<Map<String,Object>>> list(@RequestParam(defaultValue="") String search,
                                                       @RequestParam(required=false) List<String> status,
                                                       @RequestParam(required=false) List<String> visibility,
                                                       @RequestParam(required=false) String sort,
                                                       @RequestParam(required=false) String direction,
                                                       @RequestParam(defaultValue="0") int page,
                                                       @RequestParam(defaultValue="20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return BaseResponse.ofSucceeded(adminMapper.findPlans(search, status, visibility,
                AdminListSort.field(sort, PLAN_SORT_FIELDS), AdminListSort.descending(direction),
                safeSize, Math.max(page,0)*safeSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'plans','get')")
    public BaseResponse<Map<String,Object>> detail(@PathVariable UUID id) {
        Map<String,Object> result = adminMapper.findPlanDetail(id);
        result.put("activities", adminMapper.findPlanActivities(id));
        result.put("members", adminMapper.findPlanMembers(id));
        return BaseResponse.ofSucceeded(result);
    }
}
