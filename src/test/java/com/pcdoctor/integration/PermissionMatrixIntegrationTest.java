package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权矩阵（#6.2）：未登录/用户/电医/管理员 × 代表接口。
 */
class PermissionMatrixIntegrationTest extends BaseIntegrationTest {

    private String userToken;
    private String doctorToken;
    private String adminToken;
    private long appt;

    private void prepareRoles() throws Exception {
        adminToken = adminToken();
        userToken = register(nextUserId("mx-u"));
        doctorToken = register(nextUserId("mx-d"));
        promoteToDoctor(adminToken, userIdOf(doctorToken));
        appt = submitAppointment(userToken, "matrix test", 1L);
    }

    @Test
    @DisplayName("GET /api/admin/users：未登录 401 / 用户 403 / 电医 403 / 管理员 200")
    void adminUsersMatrix() throws Exception {
        prepareRoles();
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("接单：未登录 401 / 用户 403 / 电医 200 / 管理员 403")
    void acceptMatrix() throws Exception {
        prepareRoles();
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("配置写：未登录 401 / 用户 403 / 电医 200 / 管理员 200")
    void configPutMatrix() throws Exception {
        prepareRoles();
        String body = "{\"value\":\"636956662\"}";
        mockMvc.perform(put("/api/config/qq-group").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/config/qq-group").header("Authorization", "Bearer " + userToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/config/qq-group").header("Authorization", "Bearer " + doctorToken)
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/api/config/qq-group").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("文档写：未登录 401 / 用户 403 / 电医 403 / 管理员 200")
    void documentPostMatrix() throws Exception {
        prepareRoles();
        String body = "{\"title\":\"matrix-doc\",\"summary\":\"\",\"file\":\"/api/files/doc/x.md\"}";
        mockMvc.perform(post("/api/documents").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/documents").header("Authorization", "Bearer " + userToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/documents").header("Authorization", "Bearer " + doctorToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/documents").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("提交预约：未登录 401，其余角色均可")
    void submitMatrix() throws Exception {
        prepareRoles();
        String body = "{\"problem_description\":\"m\",\"category_id\":1}";
        mockMvc.perform(post("/api/appointments").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/appointments").header("Authorization", "Bearer " + userToken)
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/appointments").header("Authorization", "Bearer " + doctorToken)
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/appointments").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(0));
    }

    private String userIdOf(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString())
                .at("/data/userId").asString();
    }
}