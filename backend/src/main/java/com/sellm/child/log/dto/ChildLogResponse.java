package com.sellm.child.log.dto;

import com.sellm.child.log.ChildLog;
import com.sellm.common.LogType;

public class ChildLogResponse {
    private final Long id;
    private final String logType;
    private final String logTypeLabel;
    private final String content;
    private final Long authorUserId;
    private final String createdAt;

    public ChildLogResponse(Long id, String logType, String content, Long authorUserId, String createdAt) {
        this.id = id;
        this.logType = logType;
        this.logTypeLabel = LogType.labelOf(logType);
        this.content = content;
        this.authorUserId = authorUserId;
        this.createdAt = createdAt;
    }

    public static ChildLogResponse of(ChildLog log) {
        return new ChildLogResponse(log.getId(), log.getLogType(), log.getContent(),
            log.getAuthorUserId(), log.getCreatedAt());
    }

    public Long getId() { return id; }
    public String getLogType() { return logType; }
    public String getLogTypeLabel() { return logTypeLabel; }
    public String getContent() { return content; }
    public Long getAuthorUserId() { return authorUserId; }
    public String getCreatedAt() { return createdAt; }
}
