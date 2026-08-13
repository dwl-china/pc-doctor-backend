package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 预约闭环与权限（#5.2、#9、#6.2）。
 */
class AppointmentFlowIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("全流程：提交→接单→完成→留言，状态机与时间戳正确")
    void fullFlow() throws Exception {
        String adminToken = adminToken();
        String userToken = register(nextUserId("u"));
        String doctorToken = register(nextUserId("d"));
        promoteToDoctor(adminToken, userIdOf(doctorToken));

        long appt = submitAppointment(userToken, "computer cannot boot", 1L);
        // 接单
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.appointmentTime").isNotEmpty());
        // 完成
        mockMvc.perform(post("/api/appointments/" + appt + "/complete")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.doneTime").isNotEmpty());
        // 留言
        mockMvc.perform(post("/api/appointments/" + appt + "/messages")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json").content("{\"message\":\"is it fixed?\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("is it fixed?"));
        mockMvc.perform(get("/api/appointments/" + appt + "/messages"))
                .andExpect(jsonPath("$.data[0].userName").isNotEmpty());
    }

    @Test
    @DisplayName("状态机：重复接单被 CAS 拦截（2003）")
    void doubleAccept() throws Exception {
        String adminToken = adminToken();
        String userToken = register(nextUserId("u"));
        String doctorToken = register(nextUserId("d"));
        promoteToDoctor(adminToken, userIdOf(doctorToken));
        long appt = submitAppointment(userToken, "issue", 1L);
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                .header("Authorization", "Bearer " + doctorToken));
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(jsonPath("$.code").value(2003));
    }

    @Test
    @DisplayName("权限：他人改单 3002、本人改未接单单成功、已接单单不可删（#9）")
    void ownershipRules() throws Exception {
        String adminToken = adminToken();
        String u1 = register(nextUserId("a"));
        String u2 = register(nextUserId("b"));
        String doctor = register(nextUserId("doc"));
        promoteToDoctor(adminToken, userIdOf(doctor));

        long appt = submitAppointment(u2, "u2's appointment", 1L);
        // u1 改 u2 的单 → 3002
        mockMvc.perform(put("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + u1)
                        .contentType("application/json")
                        .content("{\"problem_description\":\"hijack\"}"))
                .andExpect(jsonPath("$.code").value(3002));
        // u2 改自己的单 → 成功
        mockMvc.perform(put("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + u2)
                        .contentType("application/json")
                        .content("{\"problem_description\":\"my own edit\"}"))
                .andExpect(jsonPath("$.code").value(0));
        // 电医接单后 u2 删除 → 3002
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                .header("Authorization", "Bearer " + doctor));
        mockMvc.perform(delete("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + u2))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("mine=1：未登录 401；登录后仅返回自己的单")
    void mineFilter() throws Exception {
        String u1 = register(nextUserId("m1"));
        String u2 = register(nextUserId("m2"));
        long own = submitAppointment(u1, "u1 appt", 1L);
        submitAppointment(u2, "u2 appt", 1L);

        mockMvc.perform(get("/api/appointments?mine=1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/appointments?mine=1").header("Authorization", "Bearer " + u1))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(own));
    }

    private String userIdOf(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString())
                .at("/data/userId").asString();
    }
}