package com.sellm.agentcommon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractHttpSmartLayerClientTest {

    // 子类暴露 send 供测试(模板方法可被覆写注入假响应)
    static class TestableClient extends AbstractHttpSmartLayerClient {
        TestableClient(SmartLayerProperties props) { super(props); }
        String callSend(String path, String body) { return send(path, body); }
    }

    private SmartLayerProperties props() {
        SmartLayerProperties p = new SmartLayerProperties();
        p.setBaseUrl("http://localhost:59999"); // 不可达端口
        p.setTimeoutSeconds(1);
        return p;
    }

    @Test
    void 不可达地址抛SmartLayerException() {
        TestableClient c = new TestableClient(props());
        assertThrows(SmartLayerException.class,
            () -> c.callSend("/v1/agents/x/invoke", "{}"));
    }

    // 覆写 send 注入假响应的子类(各 agent 测试桩依赖此模式)
    static class StubbedClient extends AbstractHttpSmartLayerClient {
        StubbedClient(SmartLayerProperties props) { super(props); }
        @Override protected String send(String path, String jsonBody) {
            return "{\"content\":\"fake\"}";
        }
        String callSend() { return send("/v1/agents/x/invoke", "{}"); }
    }

    @Test
    void 子类可覆写send注入假响应() {
        StubbedClient c = new StubbedClient(props());
        // 覆写生效、返回假响应、不真连网(若未覆写会因不可达地址抛异常)
        assertEquals("{\"content\":\"fake\"}", c.callSend());
    }
}
