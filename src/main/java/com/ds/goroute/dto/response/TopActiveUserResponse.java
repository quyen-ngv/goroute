package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One row of the Feed's "Top active users" ranking. {@code score} is the sum of the
 * points described on {@code UserMapper.selectTopActiveUsers}; the two counts are the
 * records that earned them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopActiveUserResponse {
    private UUID id;
    private String fullName;
    private String username;
    private String avatarUrl;
    private int score;
    /** Public check-ins, with or without a review. */
    private int checkinCount;
    /** Public trips with more than five activities: the only trips that score. */
    private int tripCount;
}
