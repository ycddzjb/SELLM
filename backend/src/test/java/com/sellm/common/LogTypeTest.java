package com.sellm.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LogTypeTest {

    @Test
    void 三类记录齐全且有中文标签() {
        assertThat(LogType.values()).hasSize(3);
        assertThat(LogType.CLASSROOM_TRACK.getLabel()).isEqualTo("课堂追踪");
        assertThat(LogType.HOME_COMMUNICATION.getLabel()).isEqualTo("家校沟通");
        assertThat(LogType.STAGE_REVIEW.getLabel()).isEqualTo("阶段复盘");
    }

    @Test
    void 合法码校验通过() {
        LogType.validate("CLASSROOM_TRACK");
        LogType.validate("STAGE_REVIEW");
    }

    @Test
    void 空或非法码校验抛异常() {
        assertThatThrownBy(() -> LogType.validate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogType.validate("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogType.validate("NOPE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelOf兜底() {
        assertThat(LogType.labelOf("HOME_COMMUNICATION")).isEqualTo("家校沟通");
        assertThat(LogType.labelOf("")).isEqualTo("");
        assertThat(LogType.labelOf("UNKNOWN")).isEqualTo("UNKNOWN");
    }
}
