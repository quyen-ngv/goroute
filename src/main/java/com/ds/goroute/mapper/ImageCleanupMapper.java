package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface ImageCleanupMapper {

    @SelectProvider(type = SqlProvider.class, method = "selectRows")
    List<Map<String, Object>> selectRows(@Param("sql") String sql, @Param("id") UUID id);

    class SqlProvider {
        public String selectRows(Map<String, Object> params) {
            String sql = (String) params.get("sql");
            return sql.replace("?", "#{id,javaType=java.util.UUID,jdbcType=OTHER}");
        }
    }
}
