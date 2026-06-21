package com.sellm.qa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class QaRepositoryTest {

    @Autowired QaConversationRepository convRepo;
    @Autowired QaMessageRepository msgRepo;

    @Test
    void 会话保存回填id并能按用户查询() {
        QaConversation c = new QaConversation();
        c.setUserId(99L);  // 用独立 userId 避免与 QaAskApiTest(userId=7) 数据库状态干扰
        c.setTitle("孤独症政策");
        QaConversation saved = convRepo.save(c);
        assertNotNull(saved.getId());

        List<QaConversation> mine = convRepo.listByUser(99L);
        assertEquals(1, mine.size());
        assertEquals("孤独症政策", mine.get(0).getTitle());
    }

    @Test
    void 消息保存并按会话查询有序() {
        QaConversation c = new QaConversation();
        c.setUserId(99L);
        c.setTitle("x");
        Long cid = convRepo.save(c).getId();

        QaMessage u = new QaMessage();
        u.setConversationId(cid); u.setRole("USER"); u.setContent("问题");
        msgRepo.save(u);
        QaMessage a = new QaMessage();
        a.setConversationId(cid); a.setRole("ASSISTANT"); a.setContent("答案");
        a.setRouteTo(null); a.setSources("[]");
        msgRepo.save(a);

        List<QaMessage> msgs = msgRepo.listByConversation(cid);
        assertEquals(2, msgs.size());
        assertEquals("USER", msgs.get(0).getRole());
        assertEquals("ASSISTANT", msgs.get(1).getRole());
    }
}
