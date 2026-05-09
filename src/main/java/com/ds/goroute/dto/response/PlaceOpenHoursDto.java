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
public class PlaceOpenHoursDto {
    private Map<String, List<String>> hours; // e.g., {"Monday": ["9 AM - 5 PM"], "Tuesday": [...]}
}
