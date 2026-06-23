package com.sellm.aids;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 文生素材异步:测试用同步 executor,使 POST 返回后任务已达终态,可直接断言。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubClient stub;

    @TestConfiguration
    static class Cfg {
        // 覆盖 assetTaskExecutor 为同步,@Async 在调用线程同步执行
        @Bean("assetTaskExecutor") @Primary
        TaskExecutor syncExecutor() { return new SyncTaskExecutor(); }
        @Bean @Primary StubClient stubClient() { return new StubClient(); }
    }
    static class StubClient implements SmartLayerClient {
        final AtomicReference<String> lastPrompt = new AtomicReference<>();
        volatile boolean throwError = false;
        volatile boolean returnMedia = false;
        @Override public GeneratedContent generate(String type, String prompt) {
            if (throwError) throw new com.sellm.agentcommon.SmartLayerException("down");
            lastPrompt.set(prompt);
            if (returnMedia) {
                return new GeneratedContent("[AI 生成] " + type + " 描述",
                    new byte[]{(byte) 0x89, 'P', 'N', 'G'}, "image/png", "png");
            }
            return GeneratedContent.text("[AI 生成] " + type + " 素材描述");
        }
    }

    private long submit(long uid, String type, String prompt) throws Exception {
        var res = mvc.perform(post("/api/aids/assets").header("X-User-Id", String.valueOf(uid))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("type", type, "prompt", prompt))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.taskId").exists())
            .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data").path("taskId").asLong();
    }

    @Test
    void 提交后同步达成SUCCESS并落storageKey() throws Exception {
        stub.throwError = false;
        stub.returnMedia = false;
        long taskId = submit(7L, "PICTUREBOOK", "为自闭症儿童设计认识情绪的绘本");
        mvc.perform(get("/api/aids/tasks/" + taskId).header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.result.storageKey").value("asset/" + taskId + ".txt"))
            .andExpect(jsonPath("$.data.result.type").value("PICTUREBOOK"));
    }

    @Test
    void 有媒体时按ext落盘并返mimeType() throws Exception {
        stub.throwError = false;
        stub.returnMedia = true;
        long taskId = submit(7L, "IMAGE", "视觉支持卡片");
        mvc.perform(get("/api/aids/tasks/" + taskId).header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.result.storageKey").value("asset/" + taskId + ".png"))
            .andExpect(jsonPath("$.data.result.mimeType").value("image/png"));
        stub.returnMedia = false;
    }

    @Test
    void Python不可用任务FAILED() throws Exception {
        stub.throwError = true;
        long taskId = submit(7L, "IMAGE", "情绪卡片");
        mvc.perform(get("/api/aids/tasks/" + taskId).header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.error").value(org.hamcrest.Matchers.containsString("智能层")));
        stub.throwError = false;
    }

    @Test
    void prompt空返回400不建任务() throws Exception {
        mvc.perform(post("/api/aids/assets").header("X-User-Id", "7").contentType("application/json")
                .content(json.writeValueAsString(Map.of("type", "IMAGE", "prompt", "  "))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 不支持的类型返回400() throws Exception {
        mvc.perform(post("/api/aids/assets").header("X-User-Id", "7").contentType("application/json")
                .content(json.writeValueAsString(Map.of("type", "HOLOGRAM", "prompt", "x"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 他人素材403() throws Exception {
        long taskId = submit(7L, "IMAGE", "卡片");
        mvc.perform(get("/api/aids/assets/" + taskId).header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/aids/tasks/" + taskId).header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 不存在的taskId返回404() throws Exception {
        mvc.perform(get("/api/aids/tasks/999999").header("X-User-Id", "7"))
            .andExpect(status().isNotFound());
    }

    @Test
    void 取产物原始字节() throws Exception {
        stub.throwError = false;
        stub.returnMedia = true;
        long taskId = submit(7L, "IMAGE", "卡片");
        mvc.perform(get("/api/aids/assets/" + taskId + "/raw").header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));
        stub.returnMedia = false;
    }

    @Test
    void 取他人产物原始字节403() throws Exception {
        stub.returnMedia = true;
        long taskId = submit(7L, "IMAGE", "卡片");
        mvc.perform(get("/api/aids/assets/" + taskId + "/raw").header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
        stub.returnMedia = false;
    }

    @Test
    void 缺少身份头401() throws Exception {
        mvc.perform(post("/api/aids/assets").contentType("application/json")
                .content(json.writeValueAsString(Map.of("type", "IMAGE", "prompt", "x"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 我的素材列表() throws Exception {
        submit(20L, "IMAGE", "卡片A");
        submit(20L, "AUDIO", "音频B");
        mvc.perform(get("/api/aids/assets").header("X-User-Id", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void 出网prompt已脱敏() throws Exception {
        stub.throwError = false;
        // prompt 含手机号,默认 RegexAnonymizer 会脱敏为占位符
        submit(7L, "IMAGE", "联系老师 13800138000 设计卡片");
        assertNotNull(stub.lastPrompt.get());
        assertFalse(stub.lastPrompt.get().contains("13800138000"), "出网 prompt 不应含原始手机号");
        assertTrue(stub.lastPrompt.get().contains("[电话1]"), "应替换为电话占位符");
    }
}
