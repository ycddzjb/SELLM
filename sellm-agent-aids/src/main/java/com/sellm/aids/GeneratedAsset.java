package com.sellm.aids;

/** 文生素材产物实体。prompt 明文落库,出网前脱敏;taskId = id 字符串(简化)。 */
public class GeneratedAsset {
    private Long id;
    private Long ownerId;
    private String type;        // AssetType
    private String prompt;
    private String storageKey;  // SUCCESS 后产物对象 key
    private String taskId;      // = id 字符串
    private String status;      // AssetStatus
    private String error;       // FAILED 时原因

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
