package com.sellm.scale;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScaleMapper {

    Map<String, Object> findScaleById(@Param("scaleId") String scaleId);

    List<Map<String, Object>> findItems(@Param("scaleId") String scaleId);

    List<Map<String, Object>> findBands(@Param("scaleId") String scaleId);
}
