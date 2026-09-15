package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface AdminRoleManagementMapper {
    
    List<Map<String, Object>> findAllRoles();
    
    List<Map<String, Object>> findAllAdminUsers(@Param("search") String search);
    
    List<Map<String, Object>> searchAllUsers(@Param("search") String search);
    
    Map<String, Object> findUserWithRoles(@Param("userId") UUID userId);
    
    int deleteUserRole(@Param("userId") UUID userId, @Param("roleCode") String roleCode);
}
