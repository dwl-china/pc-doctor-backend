package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全回归测试（#6.3、#6.4）：
 * - SQL 注入（登录、用户搜索、预约筛选）
 * - 上传白名单与路径穿越
 */
class SecurityRegressionIntegrationTest extends BaseIntegrationTest {

    // ---------- SQL 注入（#1、#6.3） ----------

    @Test
    @DisplayName("登录注入：万能密码与注释符均返回 1003，且 user_table 完好")
    void loginInjection() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"user_id\":\"admin\",\"password\":\"' OR '1'='1\"}"))
                .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"user_id\":\"admin'--\",\"password\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"user_id\":\"admin' OR '1'='1\",\"password\":\"' OR '1'='1\"}"))
                .andExpect(jsonPath("$.code").value(1003));
        // 表未被注入破坏：正常登录仍可用
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"user_id\":\"admin\",\"password\":\"" + adminPassword + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("用户搜索注入：注入串按字面量处理，返回空且不报错")
    void userSearchInjection() throws Exception {
        String adminToken = adminToken();
        mockMvc.perform(get("/api/admin/users?user_id=' OR '1'='1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("预约筛选注入：注入串按字面量处理，返回空且不报错")
    void appointmentFilterInjection() throws Exception {
        mockMvc.perform(get("/api/appointments?user_id=' OR '1'='1"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/appointments?status=1;DROP TABLE user_table;--"))
                .andExpect(status().isOk());
    }

    // ---------- 越权与路径（#2、#5、#9、#6.4） ----------

    @Test
    @DisplayName("注册提权回归：level/must_change_password 字段被白名单忽略")
    void registerPrivilegeEscalation() throws Exception {
        String userId = nextUserId("esc");
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"user_id\":\"" + userId + "\",\"password\":\"Passw0rd!123\","
                                + "\"user_name\":\"esc\",\"level\":2,\"must_change_password\":0,"
                                + "\"session_id\":\"x\",\"expiration_time\":\"y\"}"))
                .andExpect(jsonPath("$.data.user.level").value(0));
    }

    @Test
    @DisplayName("电医改他人单回归：3002（#9）")
    void doctorCannotTouchOthers() throws Exception {
        String adminToken = adminToken();
        String u1 = register(nextUserId("v-u1"));
        String u2 = register(nextUserId("v-u2"));
        String doctor = register(nextUserId("v-d"));
        promoteToDoctor(adminToken, userIdOf(doctor));
        long appt = submitAppointment(u1, "victim appt", 1L);
        // 电医对未接单的他人预约改单 → 3002
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + doctor)
                        .contentType("application/json")
                        .content("{\"problem_description\":\"doctor hijack\"}"))
                .andExpect(jsonPath("$.code").value(3002));
        // 他人删除同样拒绝
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + u2))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("路径穿越：下载文件名白名单拒绝 ../ 与编码斜杠（#5）")
    void pathTraversal() throws Exception {
        // 编码斜杠/多余路径段：容器或防火墙直接 4xx
        mockMvc.perform(get("/api/files/doc/..%2F..%2Fapplication.yml"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/files/../application.yml"))
                .andExpect(status().is4xxClientError());
        // 字面 .. 名：到达控制器后被白名单拒绝
        mockMvc.perform(get("/api/files/doc/.."))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("上传白名单：exe/无扩展名拒绝（4001）")
    void uploadWhitelist() throws Exception {
        String token = register(nextUserId("up"));
        MockMultipartFile exe = new MockMultipartFile("file", "evil.exe",
                "application/octet-stream", new byte[]{0x4D, 0x5A});
        mockMvc.perform(multipart("/api/files").file(exe).param("type", "doc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(4001));
    }

    private String userIdOf(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString())
                .at("/data/userId").asString();
    }
}