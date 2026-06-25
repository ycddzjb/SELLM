package com.sellm.aids;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TeachingAidMapper {
    /** disorderType 为 null 时返回全部;非 null 时按 JSON 字符串包含匹配。 */
    List<Map<String, Object>> findByDisorderType(@Param("disorderType") String disorderType);
}
