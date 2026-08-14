package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

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
    @DisplayName("doctor_id 筛选：电医工作台按自己接的单过滤（§4.4）")
    void doctorFilter() throws Exception {
        String adminToken = adminToken();
        String u = register(nextUserId("df"));
        String d1 = register(nextUserId("d1"));
        String d2 = register(nextUserId("d2"));
        promoteToDoctor(adminToken, userIdOf(d1));
        promoteToDoctor(adminToken, userIdOf(d2));
        long appt1 = submitAppointment(u, "appt 1", 1L);
        long appt2 = submitAppointment(u, "appt 2", 1L);
        mockMvc.perform(post("/api/appointments/" + appt1 + "/accept")
                .header("Authorization", "Bearer " + d1));
        mockMvc.perform(post("/api/appointments/" + appt2 + "/accept")
                .header("Authorization", "Bearer " + d2));

        mockMvc.perform(get("/api/appointments?status=1&doctor_id=" + userIdOf(d1)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(appt1));
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
        // 电医接单后 u2 删除自己的单 → 2003（本人但状态不允许）
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                .header("Authorization", "Bearer " + doctor));
        mockMvc.perform(delete("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + u2))
                .andExpect(jsonPath("$.code").value(2003));
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

    @Test
    @DisplayName("回归（走查 bug#1）：有留言的排队预约，本人撤销成功，留言一并清除")
    void deleteWithMessages() throws Exception {
        String u = register(nextUserId("dm"));
        long appt = submitAppointment(u, "has messages", 1L);
        mockMvc.perform(post("/api/appointments/" + appt + "/messages")
                        .header("Authorization", "Bearer " + u)
                        .contentType("application/json").content("{\"message\":\"will be deleted\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + u))
                .andExpect(jsonPath("$.code").value(0));
        // 预约已删，留言随之外键关联失效（3001 预约不存在，而非删除时 500）
        mockMvc.perform(get("/api/appointments/" + appt + "/messages"))
                .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    @DisplayName("回归（走查建议#6）：预约图片公开响应脱敏，本人/接单电医/管理员可见")
    void pictureSanitized() throws Exception {
        String adminToken = adminToken();
        String u = register(nextUserId("pic"));
        String d = register(nextUserId("picd"));
        promoteToDoctor(adminToken, userIdOf(d));
        long appt = submitAppointmentWithPicture(u, "with picture");
        String pic = "/api/files/appointment/regression-pic.png";

        // 公开列表：图片字段不出现
        mockMvc.perform(get("/api/appointments"))
                .andExpect(jsonPath("$.data.records[0].problemPicture").doesNotExist());
        // 公开详情：不出现
        mockMvc.perform(get("/api/appointments/" + appt))
                .andExpect(jsonPath("$.data.problemPicture").doesNotExist());
        // 本人详情：可见
        mockMvc.perform(get("/api/appointments/" + appt).header("Authorization", "Bearer " + u))
                .andExpect(jsonPath("$.data.problemPicture").value(pic));
        // 接单电医详情：可见
        mockMvc.perform(post("/api/appointments/" + appt + "/accept")
                .header("Authorization", "Bearer " + d));
        mockMvc.perform(get("/api/appointments/" + appt).header("Authorization", "Bearer " + d))
                .andExpect(jsonPath("$.data.problemPicture").value(pic));
        // 管理员详情：可见
        mockMvc.perform(get("/api/appointments/" + appt).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.problemPicture").value(pic));
        // 无关用户详情：不出现
        String other = register(nextUserId("pico"));
        mockMvc.perform(get("/api/appointments/" + appt).header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.data.problemPicture").doesNotExist());
    }

    /** 带图片提交预约（图片 URL 为占位符，不实际落盘） */
    private long submitAppointmentWithPicture(String token, String description) throws Exception {
        String pic = "/api/files/appointment/regression-pic.png";
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"problem_description\":\"" + description + "\",\"category_id\":1,"
                                + "\"problem_picture\":\"" + pic + "\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    private String userIdOf(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString())
                .at("/data/userId").asString();
    }
}