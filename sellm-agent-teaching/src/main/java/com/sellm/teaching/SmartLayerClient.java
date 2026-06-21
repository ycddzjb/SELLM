package com.sellm.teaching;

/** 调 Python 智能层生成教案/课件文本。task=lesson_plan|courseware。 */
public interface SmartLayerClient {
    /**
     * @param task "lesson_plan" 或 "courseware"
     * @param iepContentOrPlan 已脱敏:lesson_plan 传 IEP 内容;courseware 传定稿教案文本
     */
    String generate(String task, String iepContentOrPlan, String disorderType, String scene, String mode);
}
