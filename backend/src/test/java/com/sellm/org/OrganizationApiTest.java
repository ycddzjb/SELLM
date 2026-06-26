package com.sellm.org;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private OrganizationRepository orgRepo;

    @BeforeEach
    void seedOrg() {
        // test profile 下 schema 建表但无种子机构,先插一个
        orgRepo.save(new Organization(null, "阳光小学", "北京"));
    }

    @Test
    void 公开机构列表免登录可访问() throws Exception {
        mvc.perform(get("/api/orgs/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].id").isNumber())
            .andExpect(jsonPath("$.data[0].name").isNotEmpty());
    }

    @Test
    void 超管一体建机构含管理员成功且管理员可登录() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa1", "secret123", "SUPER_ADMIN", null);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "星星康复中心");
        body.put("disorderTypes", "ASD,ADHD");
        body.put("province", "江苏省");
        body.put("city", "南京市");
        body.put("managerUsername", "star_mgr");
        body.put("managerPassword", "mgrpass123");

        String resp = mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isNumber())
            .andReturn().getResponse().getContentAsString();
        long newOrgId = json.readTree(resp).path("data").asLong();

        // 该管理员能登录(ACTIVE),role MANAGER、orgId 指向新机构
        String loginBody = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "star_mgr", "password", "mgrpass123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.role").value("MANAGER"))
            .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(loginBody);
        org.assertj.core.api.Assertions.assertThat(node.path("data").path("orgId").asLong())
            .isEqualTo(newOrgId);
    }

    @Test
    void 缺管理员账号密码建机构返回400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa_miss", "secret123", "SUPER_ADMIN", null);

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "name", "缺管理员机构", "region", "上海"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 管理员用户名已存在建机构返回400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa_dup", "secret123", "SUPER_ADMIN", null);
        // 先占用一个用户名
        AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "dup_mgr", "secret123", "TEACHER", 1L);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "重名管理员机构");
        body.put("managerUsername", "dup_mgr");
        body.put("managerPassword", "mgrpass123");

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 非超管建机构被拒() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_mgr1", "secret123", "MANAGER", 1L);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "无权机构");
        body.put("region", "上海");
        body.put("managerUsername", "no_perm_mgr");
        body.put("managerPassword", "mgrpass123");

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void 超管看所有机构返回列表含扩字段() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa2", "secret123", "SUPER_ADMIN", null);
        // 造一个带扩字段的机构
        orgRepo.save(new Organization(null, "扩字段机构", "杭州", "ASD,LANGUAGE", "浙江省", "杭州市"));

        mvc.perform(get("/api/orgs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].name").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.disorderTypes == 'ASD,LANGUAGE')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.province == '浙江省')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.city == '杭州市')]").isNotEmpty());
    }

    @Test
    void 公开列表也含扩字段() throws Exception {
        orgRepo.save(new Organization(null, "公开扩字段机构", "成都", "ADHD", "四川省", "成都市"));

        mvc.perform(get("/api/orgs/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.disorderTypes == 'ADHD')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.province == '四川省')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.city == '成都市')]").isNotEmpty());
    }

    @Test
    void 非超管看所有机构被拒() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_mgr2", "secret123", "MANAGER", 1L);

        mvc.perform(get("/api/orgs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void 超管编辑机构成功() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_edit_sa", "secret123", "SUPER_ADMIN", null);
        long id = orgRepo.save(new Organization(null, "待改机构", "南京", "ASD", "江苏省", "南京市")).getId();

        mvc.perform(put("/api/orgs/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "name", "已改机构", "disorderTypes", "ADHD", "province", "浙江省", "city", "杭州市"))))
            .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(orgRepo.findById(id).getName()).isEqualTo("已改机构");
    }

    @Test
    void 软删空机构后列表不含() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_del_sa", "secret123", "SUPER_ADMIN", null);
        long id = orgRepo.save(new Organization(null, "空机构待删", "南京")).getId();

        mvc.perform(delete("/api/orgs/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(orgRepo.findById(id)).isNull();
    }

    @Test
    void 机构下有用户时删被拦400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_del_busy", "secret123", "SUPER_ADMIN", null);
        long id = orgRepo.save(new Organization(null, "有用户机构", "南京")).getId();
        // 该机构下挂一个用户
        userRepo.register("busy_mgr", "pw123456", com.sellm.security.Role.MANAGER, id, "ACTIVE");

        mvc.perform(delete("/api/orgs/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
        // 未被软删
        org.assertj.core.api.Assertions.assertThat(orgRepo.findById(id)).isNotNull();
    }

    @Test
    void 批量建机构逐条容错返回成功数与失败明细() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_batch_sa", "secret123", "SUPER_ADMIN", null);

        var ok = Map.of("name", "批量机构A", "managerUsername", "batch_a", "managerPassword", "pw123456");
        var bad = Map.of("name", "批量机构B");   // 缺管理员账号密码 → 失败
        mvc.perform(post("/api/orgs/batch")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.List.of(ok, bad))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(1))
            .andExpect(jsonPath("$.data.failures.length()").value(1));
    }

    @Test
    void 非超管编辑或删除机构被拒() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_mgr3", "secret123", "MANAGER", 1L);
        long id = orgRepo.save(new Organization(null, "机构X", "南京")).getId();

        mvc.perform(put("/api/orgs/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "x"))))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/orgs/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void 创建机构重名则复用不重复建但管理员照常创建() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_dedup_sa", "secret123", "SUPER_ADMIN", null);
        // 已有同名机构
        long existingId = orgRepo.save(new Organization(null, "复用康复中心", "南京", "ASD", "江苏省", "南京市")).getId();
        int before = orgRepo.listAll().size();

        Map<String, Object> body = new HashMap<>();
        body.put("name", "复用康复中心");   // 与已有重名
        body.put("managerUsername", "reuse_mgr");
        body.put("managerPassword", "mgrpass123");

        String resp = mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long returnedId = json.readTree(resp).path("data").asLong();

        // 复用已有机构 id,机构数不变
        org.assertj.core.api.Assertions.assertThat(returnedId).isEqualTo(existingId);
        org.assertj.core.api.Assertions.assertThat(orgRepo.listAll().size()).isEqualTo(before);
        // 管理员照常创建并归到该机构(能登录,orgId 指向复用机构)
        String loginBody = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "reuse_mgr", "password", "mgrpass123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("MANAGER"))
            .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json.readTree(loginBody).path("data").path("orgId").asLong())
            .isEqualTo(existingId);
    }

    @Test
    void 重名复用时补全的障碍省市同步到已有机构() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sync_sa", "secret123", "SUPER_ADMIN", null);
        // 已有机构信息不全(无障碍/省/市)
        long id = orgRepo.save(new Organization(null, "待补全中心", null)).getId();

        Map<String, Object> body = new HashMap<>();
        body.put("name", "待补全中心");          // 重名
        body.put("disorderTypes", "ASD,ADHD");   // 补全
        body.put("province", "江苏省");
        body.put("city", "南京市");
        body.put("managerUsername", "sync_mgr");
        body.put("managerPassword", "mgrpass123");

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk());

        // 复用机构的障碍/省/市已被补全更新
        Organization after = orgRepo.findById(id);
        org.assertj.core.api.Assertions.assertThat(after.getDisorderTypes()).isEqualTo("ASD,ADHD");
        org.assertj.core.api.Assertions.assertThat(after.getProvince()).isEqualTo("江苏省");
        org.assertj.core.api.Assertions.assertThat(after.getCity()).isEqualTo("南京市");
    }
}
