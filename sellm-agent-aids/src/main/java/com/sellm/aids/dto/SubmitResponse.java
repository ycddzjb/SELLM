package com.sellm.aids.dto;

/** POST /assets 的 202 响应体:仅返回 taskId(= asset.id)。 */
public class SubmitResponse {
    private final Long taskId;
    public SubmitResponse(Long taskId) { this.taskId = taskId; }
    public Long getTaskId() { return taskId; }
}
