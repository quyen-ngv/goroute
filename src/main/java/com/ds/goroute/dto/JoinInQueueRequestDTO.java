package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinInQueueRequestDTO {

    private Long userId;

    private String eventId;

}
