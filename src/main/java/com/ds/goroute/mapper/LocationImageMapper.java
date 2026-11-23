package com.ds.goroute.mapper;

import com.ds.goroute.entity.LocationImage;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface LocationImageMapper {

    // `%` is "similarity above pg_trgm.similarity_threshold" (default 0.3) and can use the GIN trigram
    // index idx_location_images_normalized; the explicit > 0.3 keeps the cut-off fixed whatever the setting.
    // An exact name wins, then the closest name; priority only breaks ties. Sorting by priority first
    // sent "tay ninh" to Ninh Binh (a higher-priority area whose name shares the trigram "ninh").
    @Select("""
        SELECT id, image_url, priority FROM location_images
        WHERE normalized_address % #{searchTerm}
          AND similarity(normalized_address, #{searchTerm}) > 0.3
        ORDER BY
            (normalized_address = #{searchTerm}) DESC,
            (kind = 'REGION') DESC,
            similarity(normalized_address, #{searchTerm}) DESC,
            priority DESC,
            id
        LIMIT 1
        """)
    LocationImage selectBestMatch(String searchTerm);

    /** Travel regions only: every caller assigns or describes tourist areas, never whole countries. */
    @Select("SELECT * FROM location_images WHERE kind = 'REGION' ORDER BY priority DESC, created_at DESC")
    List<LocationImage> selectAll();

    /** Countries and regions, every column the public list shows; no JSONB description, no normalized_address. */
    @Select("""
        SELECT id, kind, country_code, parent_id, currency_code,
               full_address, slogan, image_url, avatar_url, city_slug, province_code,
               latitude, longitude, priority, coverage_radius_km, created_at, updated_at
        FROM location_images
        ORDER BY priority DESC, created_at DESC, id
        """)
    List<LocationImage> selectAllForList();

    @Select("SELECT * FROM location_images WHERE id = #{id}")
    LocationImage selectById(UUID id);

    @Select("SELECT id FROM location_images WHERE kind = 'COUNTRY' AND country_code = #{countryCode}")
    UUID selectCountryId(@Param("countryCode") String countryCode);

    /**
     * The tourist area a point belongs to: an area that lists the point's ward first, otherwise
     * the nearest area whose own coverage radius reaches it (V163/V170). Null outside every area.
     */
    @Select("SELECT goroute_location_image_for_point(#{latitude}::numeric, #{longitude}::numeric)")
    UUID selectAreaIdForPoint(@Param("latitude") BigDecimal latitude, @Param("longitude") BigDecimal longitude);

    @Select("""
        <script>
        SELECT id, full_address FROM location_images
        WHERE id IN
        <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<LocationImage> selectNamesByIds(@Param("ids") Collection<UUID> ids);

    @Insert("""
        INSERT INTO location_images (id, kind, country_code, parent_id, currency_code, full_address, normalized_address, slogan, description, image_url, avatar_url, city_slug, province_code, latitude, longitude, priority, coverage_radius_km, created_at, updated_at)
        VALUES (#{id}, #{kind}, #{countryCode}, #{parentId}, #{currencyCode}, #{fullAddress}, #{normalizedAddress}, #{slogan}, #{description}::jsonb, #{imageUrl}, #{avatarUrl}, #{citySlug}, #{provinceCode}, #{latitude}, #{longitude}, #{priority}, COALESCE(#{coverageRadiusKm}, 30), #{createdAt}, #{updatedAt})
        """)
    void insert(LocationImage locationImage);

    @Update("""
        UPDATE location_images
        SET kind = #{kind},
            country_code = #{countryCode},
            parent_id = #{parentId},
            currency_code = #{currencyCode},
            full_address = #{fullAddress},
            normalized_address = #{normalizedAddress},
            slogan = #{slogan},
            description = #{description}::jsonb,
            image_url = #{imageUrl},
            avatar_url = #{avatarUrl},
            city_slug = #{citySlug},
            province_code = #{provinceCode},
            latitude = #{latitude},
            longitude = #{longitude},
            priority = #{priority},
            coverage_radius_km = COALESCE(#{coverageRadiusKm}, coverage_radius_km),
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    void updateById(LocationImage locationImage);

    @Delete("DELETE FROM location_images WHERE id = #{id}")
    void deleteById(UUID id);
}
