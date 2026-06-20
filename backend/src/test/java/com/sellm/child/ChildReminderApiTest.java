package com.sellm.child;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.security.Role;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChildReminderApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ChildRepository childRepo;

    private Child childWithReassess(String name, long orgId, Long guardian, String reassessIso) {
        Child c = new Child(null, name, "ASD", orgId, guardian);
        c.setReassessDate(reassessIso);
        return childRepo.save(c);
    }

    @Test
    void 临期与逾期纳入超窗与无日期不纳入() throws Exception {
        long orgId = 100L;
        String soon = LocalDate.now().plusDays(20).toString();
        String overdue = LocalDate.now().minusDays(5).toString();
        String farIso = LocalDate.now().plusDays(40).toString();

        Child near = childWithReassess("近期娃", orgId, null, soon);
        Child od = new Child(null, "逾期娃", "ASD", orgId, null);
        od.setIepDueDate(overdue);
        od = childRepo.save(od);
        Child far = childWithReassess("超窗娃", orgId, null, farIso);
        Child none = childRepo.save(new Child(null, "无日期娃", "ASD", orgId, null));

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "rem_teacher1", "secret123", "TEACHER", orgId);

        mvc.perform(get("/api/children/reminders").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            // 近期复评纳入,overdue=false
            .andExpect(jsonPath("$.data[?(@.childId == " + near.getId() + " && @.reminderType == 'REASSESS')].overdue")
                .value(org.hamcrest.Matchers.hasItem(false)))
            // 逾期 IEP 纳入,overdue=true
            .andExpect(jsonPath("$.data[?(@.childId == " + od.getId() + " && @.reminderType == 'IEP_DUE')].overdue")
                .value(org.hamcrest.Matchers.hasItem(true)))
            // 超窗、无日期不纳入
            .andExpect(jsonPath("$.data[?(@.childId == " + far.getId() + ")]").isEmpty())
            .andExpect(jsonPath("$.data[?(@.childId == " + none.getId() + ")]").isEmpty());
    }

    @Test
    void 他机构儿童不在老师提醒中() throws Exception {
        String soon = LocalDate.now().plusDays(10).toString();
        Child other = childWithReassess("他机构娃", 101L, null, soon);

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "rem_teacher2", "secret123", "TEACHER", 102L);

        mvc.perform(get("/api/children/reminders").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.childId == " + other.getId() + ")]").isEmpty());
    }

    @Test
    void 家长只看到自己孩子的提醒() throws Exception {
        long orgId = 103L;
        String soon = LocalDate.now().plusDays(7).toString();
        long parentId = userRepo.register("rem_parent1", "secret123", Role.PARENT, orgId, "ACTIVE").getId();
        Child mine = childWithReassess("我娃", orgId, parentId, soon);
        Child notMine = childWithReassess("别人娃", orgId, null, soon);

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "rem_parent1", "secret123", "PARENT", orgId);

        mvc.perform(get("/api/children/reminders").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.childId == " + mine.getId() + ")]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.childId == " + notMine.getId() + ")]").isEmpty());
    }
}
