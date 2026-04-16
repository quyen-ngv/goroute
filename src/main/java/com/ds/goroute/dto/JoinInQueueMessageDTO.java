package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JoinInQueueMessageDTO {

    private Long userId;

    private String eventId;

    private Long sequence;

    private Long timestamp;

}
