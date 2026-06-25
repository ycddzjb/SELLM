package com.sellm.diagnosis;

/** 诊断记录:多模态诊断的结构化结果 + 报告草案。AI 只产 DRAFT,人工定稿 FINALIZED。 */
public class Diagnosis {
    private Long id;
    private Long childId;
    private Long ownerId;
    private String scaleId;          // 关联量表(维度锚点,可空)
    private String inputSummary;     // 结构化训练表现输入(JSON)
    private String dimensions;       // AI 产结构化维度(JSON:能力等级/现存障碍/能力缺陷)
    private String draft;            // 诊断报告草案(叙述)
    private String finalizedContent;
    private String status;           // DRAFT / FINALIZED

    public Diagnosis() {}

    public Diagnosis(Long id, Long childId, Long ownerId, String scaleId, String inputSummary,
                     String dimensions, String draft, String finalizedContent, String status) {
        this.id = id;
        this.childId = childId;
        this.ownerId = ownerId;
        this.scaleId = scaleId;
        this.inputSummary = inputSummary;
        this.dimensions = dimensions;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getScaleId() { return scaleId; }
    public void setScaleId(String scaleId) { this.scaleId = scaleId; }
    public String getInputSummary() { return inputSummary; }
    public void setInputSummary(String inputSummary) { this.inputSummary = inputSummary; }
    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }
    public String getDraft() { return draft; }
    public void setDraft(String draft) { this.draft = draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public void setFinalizedContent(String finalizedContent) { this.finalizedContent = finalizedContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
