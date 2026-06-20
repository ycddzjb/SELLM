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

    // ---- 写路径(阶段 B 量表库 CRUD)----
    List<Map<String, Object>> findAllScales();

    List<Map<String, Object>> findScalesByDisorderType(@Param("disorderType") String disorderType);

    void insertScale(Map<String, Object> row);

    void updateScale(Map<String, Object> row);

    void deleteScale(@Param("scaleId") String scaleId);

    void insertItem(Map<String, Object> row);

    void insertBand(Map<String, Object> row);

    void deleteItemsByScale(@Param("scaleId") String scaleId);

    void deleteBandsByScale(@Param("scaleId") String scaleId);
}
