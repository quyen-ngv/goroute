package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * How far somebody is from the next stamp.
 *
 * <p>Progress that can be seen is what creates the pull; a reward that simply appears is a
 * surprise, and a surprise motivates nobody to take the next trip. Only shown for rules
 * that are actually countable -- a yes/no condition gets its description instead.
 */
@Data
@Builder
public class PassportStampProgressResponse {
    private String code;
    private String name;
    private String description;
    private String icon;
    private int current;
    private int threshold;
    private boolean countable;
}
