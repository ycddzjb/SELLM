package com.sellm.child.log;

/** 儿童成长记录(课堂追踪/家校沟通/阶段复盘,用 logType 区分)。 */
public class ChildLog {
    private Long id;
    private Long childId;
    private String logType;
    private String content;
    private Long authorUserId;
    private String createdAt;

    public ChildLog() {
    }

    public ChildLog(Long id, Long childId, String logType, String content,
                   Long authorUserId, String createdAt) {
        this.id = id;
        this.childId = childId;
        this.logType = logType;
        this.content = content;
        this.authorUserId = authorUserId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getLogType() { return logType; }
    public void setLogType(String logType) { this.logType = logType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
