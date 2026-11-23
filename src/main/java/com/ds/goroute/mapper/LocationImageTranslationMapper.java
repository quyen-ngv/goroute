package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Table {@code location_image_translations}: a region's name per language (V296). */
@Mapper
public interface LocationImageTranslationMapper {

    // Aliases quoted: Postgres folds unquoted ones to lowercase, and the map keys are read camelCase.
    @Select("""
        SELECT location_image_id AS "locationImageId", locale AS "locale", name AS "name"
        FROM location_image_translations
        ORDER BY location_image_id, locale
        """)
    List<Map<String, Object>> findAll();

    @Delete("DELETE FROM location_image_translations WHERE location_image_id = #{locationImageId}")
    int deleteByLocationImage(@Param("locationImageId") UUID locationImageId);

    @Insert("""
        INSERT INTO location_image_translations (location_image_id, locale, name)
        VALUES (#{locationImageId}, #{locale}, #{name})
        ON CONFLICT (location_image_id, locale)
        DO UPDATE SET name = EXCLUDED.name, updated_at = NOW()
        """)
    int upsert(@Param("locationImageId") UUID locationImageId,
               @Param("locale") String locale,
               @Param("name") String name);
}
