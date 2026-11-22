package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.type.TranslationLocale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceTranslationMapper {

    /** @return 1 when the row was written, 0 when a higher-ranked source already holds it */
    int upsert(PlaceTranslation translation);

    /** {@link #upsert} that leaves the stored description alone. */
    int upsertName(PlaceTranslation translation);

    /**
     * The description only: an existing name is kept, a new row takes the entity's name. Same
     * rank rule as {@link #upsert}.
     */
    int upsertDescription(PlaceTranslation translation);

    PlaceTranslation findByPlaceIdAndLocale(@Param("placeId") UUID placeId,
                                            @Param("locale") TranslationLocale locale);

    List<PlaceTranslation> findByPlaceId(@Param("placeId") UUID placeId);

    List<PlaceTranslation> findByPlaceIds(@Param("placeIds") List<UUID> placeIds);
}
