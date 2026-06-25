package com.sellm.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.agentcommon.AbstractHttpSmartLayerClient;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.agentcommon.SmartLayerProperties;
import com.sellm.qa.dto.QaAnswer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** REST 调 Python 智能层 /v1/agents/qa/invoke。传输由 AbstractHttpSmartLayerClient 提供。 */
@Component
public class HttpSmartLayerClient extends AbstractHttpSmartLayerClient implements SmartLayerClient {

    private final ObjectMapper json = new ObjectMapper();

    public HttpSmartLayerClient(SmartLayerProperties props) {
        super(props);
    }

    @Override
    public QaAnswer generate(String anonymizedQuestion, int topK) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("question", anonymizedQuestion);
            body.put("topK", topK);
            String resp = send("/v1/agents/qa/invoke", json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            String answer = node.path("answer").asText("");
            List<Map<String, String>> sources = new ArrayList<>();
            JsonNode arr = node.path("sources");
            if (arr.isArray()) {
                for (JsonNode s : arr) {
                    Map<String, String> m = new HashMap<>();
                    m.put("title", s.path("title").asText(""));
                    m.put("source", s.path("source").asText(""));
                    sources.add(m);
                }
            }
            return new QaAnswer(answer, sources);
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层响应解析失败", e);
        }
    }
}
