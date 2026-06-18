package com.sellm.aigateway;

import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import org.springframework.stereotype.Component;

@Component
public class DefaultAiGateway implements AiGateway {

    private final Anonymizer anonymizer;
    private final AiModel aiModel;

    public DefaultAiGateway(Anonymizer anonymizer, AiModel aiModel) {
        this.anonymizer = anonymizer;
        this.aiModel = aiModel;
    }

    @Override
    public String generate(PromptRequest request) {
        // 脱敏失败会抛 AnonymizationException,直接向上传播 → 硬阻断,不调模型
        AnonymizationResult anonymized = anonymizer.anonymize(
            request.getPrompt(), request.getNames(), request.getSchools());

        String modelOutput;
        try {
            modelOutput = aiModel.complete(anonymized.getAnonymizedText());
        } catch (RuntimeException e) {
            throw new AiGatewayException("AI 模型调用失败", e);
        }

        return anonymizer.restore(modelOutput, anonymized.getRestoreMap());
    }
}
