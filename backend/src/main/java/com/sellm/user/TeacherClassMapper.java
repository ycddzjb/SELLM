package com.sellm.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 老师-班级 多对多关联(teacher_class)。 */
@Mapper
public interface TeacherClassMapper {

    void insert(@Param("teacherUserId") Long teacherUserId, @Param("classId") Long classId);

    List<Long> findClassIdsByTeacher(@Param("teacherUserId") Long teacherUserId);

    void deleteByTeacher(@Param("teacherUserId") Long teacherUserId);
}
