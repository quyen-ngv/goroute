package com.ds.goroute.mapper;

import com.ds.goroute.entity.Ward;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper
public interface WardMapper {

    /** The ward whose boundary covers the point, or null when it falls outside every one. */
    Ward findCovering(@Param("latitude") BigDecimal latitude, @Param("longitude") BigDecimal longitude);

    Ward findByCode(@Param("code") String code);

    List<Ward> findByCodes(@Param("codes") Collection<String> codes);

    List<Ward> findByProvince(@Param("provinceCode") String provinceCode);

    long countActive();

    /** A FeatureCollection of the province's wards at display resolution; null for an unknown province. */
    String displayGeoJsonByProvince(@Param("provinceCode") String provinceCode);
}
