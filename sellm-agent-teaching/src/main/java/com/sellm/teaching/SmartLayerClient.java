package com.sellm.teaching;

/** 调 Python 智能层生成教案/课件文本。task=lesson_plan|courseware。 */
public interface SmartLayerClient {
    /**
     * @param task "lesson_plan" 或 "courseware"
     * @param iepContentOrPlan 已脱敏:lesson_plan 传 IEP 内容;courseware 传定稿教案文本
     */
    String generate(String task, String iepContentOrPlan, String disorderType, String scene, String mode);

    /**
     * 教学模块通用生成(教案/课件/案例/习题)。
     * @param contentType LESSON/COURSEWARE/CASE/EXERCISE
     * @param requirement 已脱敏的标题+要求文本
     * @param optionsJson 选项 JSON(残障类型/领域/形式/学科/题型/难度/学段/方向等)
     */
    String generateContent(String contentType, String requirement, String optionsJson);
}
