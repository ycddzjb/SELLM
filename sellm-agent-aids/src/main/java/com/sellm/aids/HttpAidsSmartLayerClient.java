package com.sellm.aids;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.agentcommon.AbstractHttpSmartLayerClient;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.agentcommon.SmartLayerProperties;
import org.springframework.stereotype.Component;

/** REST 调 Python /v1/agents/aids/invoke。传输由 AbstractHttpSmartLayerClient 提供。 */
@Component
public class HttpAidsSmartLayerClient extends AbstractHttpSmartLayerClient implements SmartLayerClient {

    private final ObjectMapper json = new ObjectMapper();

    public HttpAidsSmartLayerClient(SmartLayerProperties props) {
        super(props);
    }

    @Override
    public GeneratedContent generate(String type, String prompt) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("type", type);
            body.put("prompt", prompt);
            String resp = send("/v1/agents/aids/invoke", json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            String text = node.path("content").asText("");
            String mediaB64 = node.path("media_b64").asText("");
            if (mediaB64 != null && !mediaB64.isBlank()) {
                byte[] media = java.util.Base64.getDecoder().decode(mediaB64);
                String mimeType = node.path("mime_type").asText("application/octet-stream");
                String ext = node.path("ext").asText("bin");
                return new GeneratedContent(text, media, mimeType, ext);
            }
            return GeneratedContent.text(text);
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层响应解析失败", e);
        }
    }
}
