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

    /**
     * 据要求 AIGC 出"提示词"(教师可编辑),供随后据提示词生成正文。
     * 默认复用 generateContent(stub/旧实现无需单独实现)。
     */
    default String generatePrompt(String contentType, String requirement, String optionsJson) {
        return generateContent(contentType, requirement, optionsJson);
    }

    /**
     * 基于已定稿教案正文生成 PPT 课件文本。默认复用 generateContent(type=COURSEWARE)。
     */
    default String generateCoursewareFromLesson(String lessonContent, String optionsJson) {
        return generateContent("COURSEWARE", lessonContent, optionsJson);
    }
}
