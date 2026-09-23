package com.ds.goroute.repository;

import com.ds.goroute.entity.Ward;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WardRepository {

    Optional<Ward> findCovering(BigDecimal latitude, BigDecimal longitude);

    Optional<Ward> findByCode(String code);

    List<Ward> findByCodes(Collection<String> codes);

    List<Ward> findByProvince(String provinceCode);

    long countActive();

    Optional<String> displayGeoJsonByProvince(String provinceCode);
}
