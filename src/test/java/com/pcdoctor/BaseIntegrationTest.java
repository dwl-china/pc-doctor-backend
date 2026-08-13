package com.pcdoctor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 集成测试基类：
 * - MockMvc + test profile；@Transactional 保证每用例回滚（不污染开发库）
 * - 统一登录/注册工具方法；测试用户 id 带随机后缀避免与开发数据冲突
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "Passw0rd!123";
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${test.admin-password}")
    protected String adminPassword;

    // ---------- 工具 ----------

    /** 注册白名单只允许 [A-Za-z0-9_]，这里对前缀消毒，避免测试 id 被参数校验拒绝 */
    protected String nextUserId(String prefix) {
        String safe = prefix.replaceAll("[^A-Za-z0-9_]", "_");
        return safe + "_" + System.nanoTime() % 1_000_000 + "_" + SEQ.incrementAndGet();
    }

    /** 注册并返回 token */
    protected String register(String userId) throws Exception {
        return register(userId, DEFAULT_PASSWORD);
    }

    protected String register(String userId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"user_id\":\"" + userId + "\",\"password\":\"" + password
                                + "\",\"user_name\":\"t-" + userId + "\"}"))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("注册失败: " + result.getResponse().getContentAsString());
        }
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/token").asString();
    }

    /** 登录并返回 token */
    protected String login(String userId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"user_id\":\"" + userId + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("登录失败: " + result.getResponse().getContentAsString());
        }
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/token").asString();
    }

    protected String adminToken() throws Exception {
        return login("admin", adminPassword);
    }

    /** 把用户提升为电医（管理员操作） */
    protected void promoteToDoctor(String adminToken, String userId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/users/" + userId + "/level")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"level\":1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(0));
    }

    /** 提交预约并返回 id */
    protected long submitAppointment(String token, String description, long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"problem_description\":\"" + description + "\",\"category_id\":" + categoryId + "}"))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("提交预约失败: " + result.getResponse().getContentAsString());
        }
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    protected ResultActions getJson(String url) throws Exception {
        return mockMvc.perform(get(url));
    }
}