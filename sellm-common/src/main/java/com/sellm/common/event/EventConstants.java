package com.sellm.common.event;

/**
 * RabbitMQ 事件总线常量。
 * Exchange: sellm.agent.events (topic)
 * Routing key 规范: {agent}.{entity}.{action}
 */
public final class EventConstants {
    private EventConstants() {}

    public static final String EXCHANGE = "sellm.agent.events";
    public static final String DLQ = "sellm.agent.dlq";

    // 评估干预 Agent
    public static final String ASSESSMENT_REPORT_FINALIZED = "assessment.report.finalized";
    public static final String ASSESSMENT_IEP_FINALIZED = "assessment.iep.finalized";

    // 教学训练 Agent
    public static final String TEACHING_LESSON_PLAN_GENERATED = "teaching.lesson-plan.generated";

    // 智能教具 Agent
    public static final String AIDS_VIDEO_GENERATED = "aids.video.generated";
    public static final String AIDS_IMAGE_GENERATED = "aids.image.generated";

    // 科研助手 Agent
    public static final String RESEARCH_PROPOSAL_DRAFTED = "research.proposal.drafted";
}
