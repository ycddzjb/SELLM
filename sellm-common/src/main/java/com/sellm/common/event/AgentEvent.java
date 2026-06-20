package com.sellm.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 跨 Agent 事件标准消息体。
 * payload 内不含明文 PII(只传 id + 脱敏字段)。
 */
public record AgentEvent(
        String eventId,
        String routingKey,
        Instant occurredAt,
        Long actorUserId,
        Long orgId,
        Map<String, Object> payload
) {
    public static AgentEvent of(String routingKey, Long actorUserId, Long orgId, Map<String, Object> payload) {
        return new AgentEvent(
                UUID.randomUUID().toString(),
                routingKey,
                Instant.now(),
                actorUserId,
                orgId,
                payload
        );
    }
}
