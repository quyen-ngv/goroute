package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.UUID;

@Data
public class UpdateConversationStatusRequest {
 @Pattern(regexp="OPEN|PENDING|RESOLVED|CLOSED|BLOCKED|ARCHIVED") private String status;
 private UUID assignedMemberId;
}
