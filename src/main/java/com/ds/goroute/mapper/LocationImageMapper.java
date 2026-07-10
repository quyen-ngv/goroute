package com.ds.goroute.mapper;

import com.ds.goroute.entity.LocationImage;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LocationImageMapper {

    @Select("""
        SELECT * FROM location_images
        WHERE similarity(normalized_address, #{searchTerm}) > 0.3
        ORDER BY
            priority DESC,
            similarity(normalized_address, #{searchTerm}) DESC
        LIMIT 1
        """)
    LocationImage selectBestMatch(String searchTerm);

    @Select("SELECT * FROM location_images ORDER BY priority DESC, created_at DESC")
    List<LocationImage> selectAll();

    @Select("SELECT * FROM location_images WHERE id = #{id}")
    LocationImage selectById(UUID id);

    @Insert("""
        INSERT INTO location_images (id, full_address, normalized_address, image_url, avatar_url, city_slug, latitude, longitude, priority, created_at, updated_at)
        VALUES (#{id}, #{fullAddress}, #{normalizedAddress}, #{imageUrl}, #{avatarUrl}, #{citySlug}, #{latitude}, #{longitude}, #{priority}, #{createdAt}, #{updatedAt})
        """)
    void insert(LocationImage locationImage);

    @Update("""
        UPDATE location_images
        SET full_address = #{fullAddress},
            normalized_address = #{normalizedAddress},
            image_url = #{imageUrl},
            avatar_url = #{avatarUrl},
            city_slug = #{citySlug},
            latitude = #{latitude},
            longitude = #{longitude},
            priority = #{priority},
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    void updateById(LocationImage locationImage);

    @Delete("DELETE FROM location_images WHERE id = #{id}")
    void deleteById(UUID id);
}
