package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

/**
 * What posts mean for this viewer right now (GET /v1/api/posts/policy). Both switches already
 * fold in the beta list, so the app never works them out itself.
 */
@Value
@Builder
public class PostPolicyResponse {
    /** POST.ENABLED, or a beta viewer: lists and sections show posts. */
    boolean enabled;
    /** POST.USER_WRITE_ENABLED, or a beta viewer, and signed in: the app may offer writing. */
    boolean canWrite;
    int maxBodyLength;
    int maxMedia;
    int maxVideoSeconds;
    int maxPlaces;
}
