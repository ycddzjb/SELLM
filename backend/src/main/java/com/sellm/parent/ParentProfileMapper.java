package com.sellm.parent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ParentProfileMapper {

    void insert(Map<String, Object> row);

    Map<String, Object> findByUserId(@Param("userId") Long userId);

    void updateChildId(@Param("userId") Long userId, @Param("childId") Long childId);

    /** 本机构家长(join app_user 取 org/status/username + class_room 取班级名)。 */
    List<Map<String, Object>> findByOrg(@Param("orgId") Long orgId);

    /** 分派给某老师且 app_user 状态为 PENDING 的家长(老师待审列表)。 */
    List<Map<String, Object>> findPendingByTeacher(@Param("teacherId") Long teacherId);
}
