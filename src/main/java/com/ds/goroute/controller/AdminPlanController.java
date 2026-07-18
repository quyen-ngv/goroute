package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {
    private final AdminMapper adminMapper;

    @GetMapping
    public BaseResponse<List<Map<String,Object>>> list(@RequestParam(defaultValue="") String search,
                                                       @RequestParam(defaultValue="0") int page,
                                                       @RequestParam(defaultValue="20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return BaseResponse.ofSucceeded(adminMapper.findPlans(search, safeSize, Math.max(page,0)*safeSize));
    }

    @GetMapping("/{id}")
    public BaseResponse<Map<String,Object>> detail(@PathVariable UUID id) {
        Map<String,Object> result = adminMapper.findPlanDetail(id);
        result.put("activities", adminMapper.findPlanActivities(id));
        result.put("members", adminMapper.findPlanMembers(id));
        return BaseResponse.ofSucceeded(result);
    }
}
