package com.ds.goroute.dto.response;

import com.ds.goroute.service.marketplace.ListingReadiness;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ListingReadinessResponse {
    boolean ready;
    int score;
    List<CheckResponse> checks;

    @Value
    @Builder
    public static class CheckResponse {
        String code;
        String label;
        boolean passed;
        boolean required;
        int weight;
        String detail;
    }

    public static ListingReadinessResponse from(ListingReadiness.Result result) {
        return ListingReadinessResponse.builder().ready(result.ready()).score(result.score())
                .checks(result.checks().stream().map(c -> CheckResponse.builder().code(c.code()).label(c.label())
                        .passed(c.passed()).required(c.required()).weight(c.weight()).detail(c.detail()).build()).toList())
                .build();
    }
}
