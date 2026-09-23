package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Link table {@code location_image_wards}: which wards make up a curated tourist area. */
@Mapper
public interface LocationImageWardMapper {

    @Select("SELECT ward_code FROM location_image_wards WHERE location_image_id = #{locationImageId} ORDER BY ward_code")
    List<String> findWardCodes(@Param("locationImageId") UUID locationImageId);

    @Select("""
        SELECT location_image_id AS locationImageId, ward_code AS wardCode
        FROM location_image_wards
        ORDER BY location_image_id, ward_code
        """)
    List<Map<String, Object>> findAllLinks();

    @Delete("DELETE FROM location_image_wards WHERE location_image_id = #{locationImageId}")
    int deleteByLocationImage(@Param("locationImageId") UUID locationImageId);

    @Insert("""
        <script>
        INSERT INTO location_image_wards (location_image_id, ward_code) VALUES
        <foreach collection="wardCodes" item="code" separator=",">(#{locationImageId}, #{code})</foreach>
        ON CONFLICT DO NOTHING
        </script>
        """)
    int insertAll(@Param("locationImageId") UUID locationImageId, @Param("wardCodes") Collection<String> wardCodes);
}
