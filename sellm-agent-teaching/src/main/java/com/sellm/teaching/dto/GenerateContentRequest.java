package com.sellm.teaching.dto;

import java.util.List;
import java.util.Map;

/** 教学内容生成请求(教案/课件/案例/习题统一)。 */
public class GenerateContentRequest {
    private String contentType;   // LESSON/COURSEWARE/CASE/EXERCISE
    private String title;
    private String requirement;   // 教师输入的内容与要求(文本)
    private Map<String, Object> options;  // 残障类型/领域/形式/学科/题型/难度/学段/方向等
    private List<String> subjectNames;     // 出网脱敏屏蔽表
    private String content;                // 定稿落库正文(草稿不落库,定稿才传)
    private Long sourceId;                 // 课件定稿关联的来源教案 id
    private Long lessonId;                 // 生成课件草稿时选定的教案 id

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
    public List<String> getSubjectNames() { return subjectNames; }
    public void setSubjectNames(List<String> subjectNames) { this.subjectNames = subjectNames; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getLessonId() { return lessonId; }
    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }
}
