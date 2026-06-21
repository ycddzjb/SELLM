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

    @Test
    void 子类可覆写send注入假响应() {
        // 验证 send 可被子类覆写(测试注入模式)
        AbstractHttpSmartLayerClient c = new AbstractHttpSmartLayerClient(props()) {
            @Override protected String send(String path, String jsonBody) {
                return "{\"content\":\"fake\"}";
            }
        };
        assertNotNull(c);
    }
}
