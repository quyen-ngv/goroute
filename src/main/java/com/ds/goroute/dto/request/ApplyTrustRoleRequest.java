package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.TrustRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplyTrustRoleRequest {

    @NotNull(message = "A role is required")
    private TrustRole role;

    /** Required for a local-expert application: expertise is always expertise somewhere. */
    @Size(max = 10)
    private String areaProvinceCode;

    @Size(max = 2000)
    @ModeratedText(contentType = ModeratedContentType.USER_PROFILE, visibility = ModerationVisibility.GROUP)
    private String applicationNote;
}
