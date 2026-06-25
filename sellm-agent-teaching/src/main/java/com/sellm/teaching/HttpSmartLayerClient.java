package com.sellm.teaching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.agentcommon.AbstractHttpSmartLayerClient;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.agentcommon.SmartLayerProperties;
import org.springframework.stereotype.Component;

/** REST 调 Python 智能层 /v1/agents/teaching/invoke。传输由 AbstractHttpSmartLayerClient 提供。 */
@Component
public class HttpSmartLayerClient extends AbstractHttpSmartLayerClient implements SmartLayerClient {

    private final ObjectMapper json = new ObjectMapper();

    public HttpSmartLayerClient(SmartLayerProperties props) {
        super(props);
    }

    @Override
    public String generate(String task, String iepContentOrPlan, String disorderType, String scene, String mode) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("task", task);
            if ("courseware".equals(task)) {
                body.put("lessonPlanContent", iepContentOrPlan);
            } else {
                body.put("iepContent", iepContentOrPlan);
            }
            body.put("disorderType", disorderType);
            body.put("scene", scene);
            body.put("mode", mode);
            String resp = send("/v1/agents/teaching/invoke", json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            return node.path("content").asText("");
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层响应解析失败", e);
        }
    }

    @Override
    public String generateContent(String contentType, String requirement, String optionsJson) {
        try {
            ObjectNode body = json.createObjectNode();
            // task 用小写 type(lesson/courseware/case/exercise),Python 据此分支
            body.put("task", contentType == null ? "" : contentType.toLowerCase());
            body.put("requirement", requirement == null ? "" : requirement);
            // options 透传(JSON 字符串解析为对象塞入,Python 直接读字段)
            if (optionsJson != null && !optionsJson.isBlank()) {
                body.set("options", json.readTree(optionsJson));
            }
            String resp = send("/v1/agents/teaching/invoke", json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            return node.path("content").asText("");
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层响应解析失败", e);
        }
    }
}
