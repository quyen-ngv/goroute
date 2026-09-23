package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Ward;
import com.ds.goroute.mapper.WardMapper;
import com.ds.goroute.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WardRepositoryImpl implements WardRepository {

    private final WardMapper mapper;

    @Override
    public Optional<Ward> findCovering(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findCovering(latitude, longitude));
    }

    @Override
    public Optional<Ward> findByCode(String code) {
        return Optional.ofNullable(mapper.findByCode(code));
    }

    @Override
    public List<Ward> findByCodes(Collection<String> codes) {
        return codes == null || codes.isEmpty() ? List.of() : mapper.findByCodes(codes);
    }

    @Override
    public List<Ward> findByProvince(String provinceCode) {
        return mapper.findByProvince(provinceCode);
    }

    @Override
    public long countActive() {
        return mapper.countActive();
    }

    @Override
    public Optional<String> displayGeoJsonByProvince(String provinceCode) {
        return Optional.ofNullable(mapper.displayGeoJsonByProvince(provinceCode));
    }
}
