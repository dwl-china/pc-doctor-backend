package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证流程集成测试（#4.3、#4.4、#4.6、#6.2）。
 */
class AuthIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("管理员登录成功返回 JWT + 用户信息")
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"user_id\":\"admin\",\"password\":\"" + adminPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.userId").value("admin"))
                .andExpect(jsonPath("$.data.user.level").value(2));
    }

    @Test
    @DisplayName("错误密码与不存在账号返回同一错误（不泄露账号是否存在，#4.6）")
    void loginDoesNotLeakExistence() throws Exception {
        String wrongPass = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"user_id\":\"admin\",\"password\":\"definitely-wrong-123\"}"))
                .andReturn().getResponse().getContentAsString();
        String ghost = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"user_id\":\"no_such_user_xyz\",\"password\":\"whatever-123\"}"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(wrongPass).isEqualTo(ghost);
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"user_id\":\"admin\",\"password\":\"definitely-wrong-123\"}"))
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("注册：白名单 DTO，level 恒为 0（#2、#4.4）")
    void registerWhitelist() throws Exception {
        String userId = nextUserId("rg");
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"user_id\":\"" + userId + "\",\"password\":\"Passw0rd!123\","
                                + "\"user_name\":\"rg-user\",\"level\":2,\"must_change_password\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.level").value(0))
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(false));
    }

    @Test
    @DisplayName("重复注册 → 1004")
    void registerDuplicate() throws Exception {
        String userId = nextUserId("dup");
        register(userId);
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"user_id\":\"" + userId + "\",\"password\":\"Passw0rd!123\",\"user_name\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("账号格式白名单：含特殊字符的账号被参数校验拒绝")
    void registerBadUserIdPattern() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"user_id\":\"x' OR '1'='1\",\"password\":\"Passw0rd!123\",\"user_name\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("me：未登录 401 + 1001；登录后返回本人信息")
    void meEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
        String userId = nextUserId("me");
        String token = register(userId);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(userId));
    }

    @Test
    @DisplayName("伪造/篡改 token → 401")
    void tamperedToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiJ9.forged-signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
    }
}