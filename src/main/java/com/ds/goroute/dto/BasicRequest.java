package com.ds.goroute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.micrometer.common.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BasicRequest implements Serializable {

    private static final long serialVersionUID = 1606619939033520333L;

    private String requestId;

    private String uri;

    private String opsUserEmail;

    private String opsUserId;

    private String opsUsername;

    private List<String> manageMerchants;

    private List<String> branchCodes;

    public boolean isValidByManageMerchants(String merchantCode) {
        if (StringUtils.isBlank(merchantCode)) {
            return true;
        }

        return new HashSet<>(manageMerchants).contains(merchantCode);
    }

}
