package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One xã / phường / đặc khu of the current 34-province model (table {@code wards}).
 *
 * <p>The geometry columns are deliberately absent: the row is what the application
 * reasons about, the shapes stay in PostGIS and are only ever asked "does this point fall
 * inside you" or "give me your simplified outline as GeoJSON".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ward {
    /** Official code, e.g. {@code 00070}. */
    private String code;
    private String provinceCode;
    private String name;
    private String nameEn;
    /** With the unit prefix: {@code Phường Hoàn Kiếm}. */
    private String fullName;
    private String fullNameEn;
    private String codeName;
    /** WARD, COMMUNE or SPECIAL_ZONE. */
    private String unitType;
    private String postalCode;
    private BigDecimal areaKm2;
    private String datasetVersion;
    private Boolean isActive;
    /** Joined from {@code provinces}; present on every read that needs to name the place. */
    private String provinceName;
}
