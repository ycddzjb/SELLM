package com.sellm.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface AppUserMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findByUsername(@Param("username") String username);
    void updateRoleOrgStatus(@Param("id") Long id, @Param("role") String role,
                             @Param("orgId") Long orgId, @Param("status") String status);
}
