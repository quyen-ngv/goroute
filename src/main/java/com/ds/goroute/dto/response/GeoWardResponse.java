package com.ds.goroute.dto.response;

import com.ds.goroute.entity.Ward;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoWardResponse {
    private String code;
    private String provinceCode;
    private String provinceName;
    private String name;
    private String nameEn;
    private String fullName;
    private String fullNameEn;
    /** WARD, COMMUNE or SPECIAL_ZONE. */
    private String unitType;
    private BigDecimal areaKm2;

    public static GeoWardResponse from(Ward ward) {
        return GeoWardResponse.builder()
                .code(ward.getCode())
                .provinceCode(ward.getProvinceCode())
                .provinceName(ward.getProvinceName())
                .name(ward.getName())
                .nameEn(ward.getNameEn())
                .fullName(ward.getFullName())
                .fullNameEn(ward.getFullNameEn())
                .unitType(ward.getUnitType())
                .areaKm2(ward.getAreaKm2())
                .build();
    }
}
