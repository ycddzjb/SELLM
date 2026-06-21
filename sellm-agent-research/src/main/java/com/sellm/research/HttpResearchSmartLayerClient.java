package com.sellm.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.agentcommon.AbstractHttpSmartLayerClient;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.agentcommon.SmartLayerProperties;
import org.springframework.stereotype.Component;

/** REST 调 Python /v1/agents/research/invoke。传输由 AbstractHttpSmartLayerClient 提供。 */
@Component
public class HttpResearchSmartLayerClient extends AbstractHttpSmartLayerClient implements SmartLayerClient {

    private final ObjectMapper json = new ObjectMapper();

    public HttpResearchSmartLayerClient(SmartLayerProperties props) {
        super(props);
    }

    @Override
    public String generate(String topic) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("topic", topic);
            String resp = send("/v1/agents/research/invoke", json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            return node.path("content").asText("");
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层响应解析失败", e);
        }
    }
}
