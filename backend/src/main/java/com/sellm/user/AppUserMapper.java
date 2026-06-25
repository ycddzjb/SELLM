package com.sellm.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AppUserMapper {
    void insert(Map<String, Object> row);
    void insertWeChat(Map<String, Object> row);
    Map<String, Object> findByUsername(@Param("username") String username);
    Map<String, Object> findByOpenid(@Param("wxOpenid") String wxOpenid);
    Map<String, Object> findById(@Param("id") Long id);
    List<Map<String, Object>> findPendingByOrg(@Param("orgId") Long orgId);
    List<Map<String, Object>> findPendingWeChat(@Param("orgId") Long orgId);
    List<Map<String, Object>> findAll();
    List<Map<String, Object>> findByOrg(@Param("orgId") Long orgId);
    void updateRoleOrgStatus(@Param("id") Long id, @Param("role") String role,
                             @Param("orgId") Long orgId, @Param("status") String status);
    void updateStatus(@Param("id") Long id, @Param("status") String status);
    void updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
    long countActiveByRole(@Param("role") String role);
}
