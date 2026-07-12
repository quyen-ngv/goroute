package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.type.TranslationLocale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceTranslationMapper {

    void upsert(PlaceTranslation translation);

    PlaceTranslation findByPlaceIdAndLocale(@Param("placeId") UUID placeId,
                                            @Param("locale") TranslationLocale locale);

    List<PlaceTranslation> findByPlaceId(@Param("placeId") UUID placeId);

    List<PlaceTranslation> findByPlaceIds(@Param("placeIds") List<UUID> placeIds);
}
