package com.sellm.qa;

import com.sellm.agentcommon.SmartLayerException;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.qa.dto.AskRequest;
import com.sellm.qa.dto.AskResponse;
import com.sellm.qa.dto.ConversationView;
import com.sellm.qa.dto.QaAnswer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QaAppService {

    private static final int DEFAULT_TOP_K = 5;

    private final QaConversationRepository convRepo;
    private final QaMessageRepository msgRepo;
    private final IntentClassifier intentClassifier;
    private final SmartLayerClient smartLayerClient;
    private final Anonymizer anonymizer;
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();

    public QaAppService(QaConversationRepository convRepo, QaMessageRepository msgRepo,
                        IntentClassifier intentClassifier, SmartLayerClient smartLayerClient,
                        Anonymizer anonymizer) {
        this.convRepo = convRepo;
        this.msgRepo = msgRepo;
        this.intentClassifier = intentClassifier;
        this.smartLayerClient = smartLayerClient;
        this.anonymizer = anonymizer;
    }

    public AskResponse ask(Long userId, AskRequest req) {
        String question = req.getQuestion();
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "问题不能为空");
        }
        // 1. 会话:取或建
        QaConversation conv = resolveConversation(userId, req.getConversationId(), question);
        // 2. 写 USER 消息(明文原问题)
        QaMessage userMsg = new QaMessage();
        userMsg.setConversationId(conv.getId());
        userMsg.setRole("USER");
        userMsg.setContent(question);
        msgRepo.save(userMsg);

        // 3. 意图分类
        Intent intent = intentClassifier.classify(question);

        AskResponse resp = new AskResponse();
        resp.setConversationId(conv.getId());

        if (intent != Intent.GENERAL) {
            // 4a. 业务意图:返深链,不调 Python(守 AI 红线)
            String answer = "这看起来是" + intent.getRouteTo() + "相关需求,建议前往对应功能页操作。";
            resp.setRouteTo(intent.getRouteTo());
            resp.setDeepLink(intent.getDeepLink());
            resp.setAnswer(answer);
            resp.setSources(List.of());
            QaMessage a = saveAssistant(conv.getId(), answer, intent.getRouteTo(), "[]");
            resp.setMessageId(a.getId());
            return resp;
        }

        // 4b. GENERAL:脱敏 → 调 Python → 还原
        String answer;
        List<Map<String, String>> sources = new ArrayList<>();
        try {
            AnonymizationResult anon = anonymizer.anonymize(question,
                safeNames(req.getSubjectNames()), List.of()); // 红线:出网必经脱敏(姓名靠调用方传入屏蔽表)
            QaAnswer qa = smartLayerClient.generate(anon.getAnonymizedText(), DEFAULT_TOP_K);
            answer = anonymizer.restore(qa.getAnswer(), anon.getRestoreMap());
            if (qa.getSources() != null) sources = qa.getSources();
        } catch (AnonymizationException ae) {
            // 脱敏失败硬阻断(绝不发送)
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            // Python 不可用:优雅降级
            answer = "问答服务暂不可用,请稍后重试。";
        }
        resp.setAnswer(answer);
        resp.setSources(sources);
        String sourcesJson = toJson(sources);
        QaMessage a = saveAssistant(conv.getId(), answer, null, sourcesJson);
        resp.setMessageId(a.getId());
        return resp;
    }

    public ConversationView getConversation(Long userId, Long conversationId) {
        QaConversation conv = convRepo.findById(conversationId);
        if (conv == null) throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        if (!conv.getUserId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该会话");
        List<ConversationView.MessageView> mvs = new ArrayList<>();
        for (QaMessage m : msgRepo.listByConversation(conversationId)) {
            mvs.add(new ConversationView.MessageView(m.getId(), m.getRole(), m.getContent(), m.getRouteTo(), m.getSources()));
        }
        return new ConversationView(conv.getId(), conv.getTitle(), mvs);
    }

    public List<ConversationView> listConversations(Long userId) {
        List<ConversationView> out = new ArrayList<>();
        for (QaConversation c : convRepo.listByUser(userId)) {
            out.add(new ConversationView(c.getId(), c.getTitle(), null));
        }
        return out;
    }

    /** subjectNames 安全转 List:null→空,过滤空白(供脱敏屏蔽表)。 */
    private static List<String> safeNames(List<String> names) {
        if (names == null) return List.of();
        return names.stream().filter(n -> n != null && !n.isBlank()).toList();
    }

    private QaConversation resolveConversation(Long userId, Long conversationId, String question) {
        if (conversationId != null) {
            QaConversation c = convRepo.findById(conversationId);
            if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
            if (!c.getUserId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该会话");
            return c;
        }
        QaConversation c = new QaConversation();
        c.setUserId(userId);
        c.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
        return convRepo.save(c);
    }

    private QaMessage saveAssistant(Long convId, String content, String routeTo, String sourcesJson) {
        QaMessage a = new QaMessage();
        a.setConversationId(convId);
        a.setRole("ASSISTANT");
        a.setContent(content);
        a.setRouteTo(routeTo);
        a.setSources(sourcesJson);
        return msgRepo.save(a);
    }

    private String toJson(List<Map<String, String>> sources) {
        try { return json.writeValueAsString(sources); }
        catch (Exception e) { return "[]"; }
    }
}
