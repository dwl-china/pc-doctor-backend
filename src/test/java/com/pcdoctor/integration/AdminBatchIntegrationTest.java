package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import com.pcdoctor.entity.Appointment;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.MessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 管理端批量操作（契约 §4.3）：部分成功 + 失败清单，逐条复用单条接口的业务规则。
 */
class AdminBatchIntegrationTest extends BaseIntegrationTest {

    @Autowired
    AppointmentMapper appointmentMapper;
    @Autowired
    MessageMapper messageMapper;

    // ---------- 工具 ----------

    private String postBatch(String url, String token, String body, int expectCode) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(jsonPath("$.code").value(expectCode))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String userIdOf(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString())
                .at("/data/userId").asString();
    }

    /** 提交举报（接口返回 Result<Void>，id 需另查列表） */
    private void fileReport(String token, long appointmentId, String reason) throws Exception {
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"target_type\":1,\"target_id\":" + appointmentId
                                + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 从管理端举报列表里按举报人 + 事由反查 id */
    private long reportIdOf(String admin, String reporterId, String reason) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports?page=1&size=100")
                .header("Authorization", "Bearer " + admin)).andReturn();
        for (var r : objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/records")) {
            if (reporterId.equals(r.at("/reporterId").asString()) && reason.equals(r.at("/reason").asString())) {
                return r.at("/id").asLong();
            }
        }
        throw new IllegalStateException("未找到举报记录: " + reason);
    }

    // ---------- 用户 ----------

    @Test
    @DisplayName("批量删用户：能删的删，有预约记录的记入失败清单（带 code 与原因）")
    void batchDeleteUsers_partialSuccess() throws Exception {
        String admin = adminToken();
        String u1 = register(nextUserId("bd1"));
        String u2 = register(nextUserId("bd2"));
        String busy = register(nextUserId("bd3"));
        submitAppointment(busy, "has appointment", 1L);

        String body = "{\"ids\":[\"" + userIdOf(u1) + "\",\"" + userIdOf(busy) + "\",\"" + userIdOf(u2) + "\"]}";
        String json = postBatch("/api/admin/users/batch/delete", admin, body, 0);

        var data = objectMapper.readTree(json).at("/data");
        assertThat(data.at("/total").asLong()).isEqualTo(3);
        assertThat(data.at("/success").asLong()).isEqualTo(2);
        assertThat(data.at("/failures").size()).isEqualTo(1);
        assertThat(data.at("/failures/0/id").asString()).isEqualTo(userIdOf(busy));
        assertThat(data.at("/failures/0/code").asInt()).isEqualTo(2003);
        assertThat(data.at("/failures/0/reason").asString()).contains("无法删除");
    }

    @Test
    @DisplayName("批量删用户：举报过别人的用户也禁止删除"
            + "（回归：漏检 fk_report_reporter 会抛 DataIntegrityViolationException → 500）")
    void batchDeleteUsers_withReportRecord() throws Exception {
        String admin = adminToken();
        String owner = register(nextUserId("rp-o"));
        String reporter = register(nextUserId("rp-r"));
        long appt = submitAppointment(owner, "reported", 1L);
        fileReport(reporter, appt, "违规内容");

        String body = "{\"ids\":[\"" + userIdOf(reporter) + "\"]}";

        String json = postBatch("/api/admin/users/batch/delete", admin, body, 0);

        var data = objectMapper.readTree(json).at("/data");
        assertThat(data.at("/success").asLong()).isZero();
        assertThat(data.at("/failures/0/code").asInt()).isEqualTo(2003);
        assertThat(data.at("/failures/0/reason").asString()).contains("举报");
    }

    @Test
    @DisplayName("批量删用户：不能删除自己（自我保护作为失败项返回，不中断整批）")
    void batchDeleteUsers_selfIsFailure() throws Exception {
        String admin = adminToken();
        String other = register(nextUserId("sd"));

        String body = "{\"ids\":[\"admin\",\"" + userIdOf(other) + "\"]}";
        String json = postBatch("/api/admin/users/batch/delete", admin, body, 0);

        var data = objectMapper.readTree(json).at("/data");
        assertThat(data.at("/success").asLong()).isEqualTo(1);
        assertThat(data.at("/failures/0/id").asString()).isEqualTo("admin");
        assertThat(data.at("/failures/0/reason").asString()).isEqualTo("不能删除自己");
    }

    @Test
    @DisplayName("批量改等级：含自己被拒，其余成功")
    void batchUpdateLevel() throws Exception {
        String admin = adminToken();
        String u1 = register(nextUserId("lv1"));
        String u2 = register(nextUserId("lv2"));

        String body = "{\"ids\":[\"" + userIdOf(u1) + "\",\"admin\",\"" + userIdOf(u2) + "\"],\"level\":1}";
        String json = postBatch("/api/admin/users/batch/level", admin, body, 0);

        var data = objectMapper.readTree(json).at("/data");
        assertThat(data.at("/success").asLong()).isEqualTo(2);
        assertThat(data.at("/failures/0/reason").asString()).isEqualTo("不能修改自己的角色");
        // 落库确认
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + u1))
                .andExpect(jsonPath("$.data.level").value(1));
    }

    // ---------- 入参校验 ----------

    @Test
    @DisplayName("批量入参：空列表 2001、超过 200 项 2001")
    void batchRequestValidation() throws Exception {
        String admin = adminToken();
        postBatch("/api/admin/users/batch/delete", admin, "{\"ids\":[]}", 2001);

        String tooMany = LongStream.rangeClosed(1, 201).boxed()
                .map(String::valueOf).collect(Collectors.joining(","));
        postBatch("/api/admin/appointments/batch/delete", admin, "{\"ids\":[" + tooMany + "]}", 2001);
    }

    // ---------- 预约 ----------

    @Test
    @DisplayName("批量改预约状态：status=1 已废弃记 2001，标记已处理并补登记电医成功")
    void batchUpdateAppointmentStatus() throws Exception {
        String admin = adminToken();
        String user = register(nextUserId("bs-u"));
        String doctor = register(nextUserId("bs-d"));
        promoteToDoctor(admin, userIdOf(doctor));
        long a1 = submitAppointment(user, "no doctor yet", 1L);
        long a2 = submitAppointment(user, "no doctor yet 2", 1L);

        // 「处理中」已删除：整批两条都以 2001 记入失败清单
        String failJson = postBatch("/api/admin/appointments/batch/status", admin,
                "{\"ids\":[" + a1 + "," + a2 + "],\"status\":1}", 0);
        assertThat(objectMapper.readTree(failJson).at("/data/success").asLong()).isZero();
        assertThat(objectMapper.readTree(failJson).at("/data/failures/0/code").asInt()).isEqualTo(2001);

        // 标记已处理并指定电医：两条都成功
        String okJson = postBatch("/api/admin/appointments/batch/status", admin,
                "{\"ids\":[" + a1 + "," + a2 + "],\"status\":2,\"doctor_id\":\"" + userIdOf(doctor) + "\"}", 0);
        assertThat(objectMapper.readTree(okJson).at("/data/success").asLong()).isEqualTo(2);

        Appointment db = appointmentMapper.selectById(a1);
        assertThat(db.getStatus()).isEqualTo(Appointment.STATUS_DONE);
        assertThat(db.getDoctorId()).isEqualTo(userIdOf(doctor));
        assertThat(db.getAppointmentTime()).isNotNull();
        assertThat(db.getDoneTime()).isNotNull();
    }

    @Test
    @DisplayName("批量删预约：含不存在的 id 记 3001，其余成功；留言级联清除、图片不阻塞")
    void batchDeleteAppointments() throws Exception {
        String admin = adminToken();
        String user = register(nextUserId("bda"));
        long a1 = submitAppointment(user, "to delete", 1L);
        long a2 = submitAppointment(user, "also delete", 1L);
        mockMvc.perform(post("/api/appointments/" + a1 + "/messages")
                .header("Authorization", "Bearer " + user)
                .contentType("application/json").content("{\"message\":\"bye\"}"));

        String body = "{\"ids\":[" + a1 + "," + a2 + ",99999999]}";
        String json = postBatch("/api/admin/appointments/batch/delete", admin, body, 0);

        var data = objectMapper.readTree(json).at("/data");
        assertThat(data.at("/success").asLong()).isEqualTo(2);
        assertThat(data.at("/failures/0/code").asInt()).isEqualTo(3001);
        assertThat(appointmentMapper.selectById(a1)).isNull();
        // 留言随预约一并清除（fk_message_appointment 无级联，靠 service 手动删）
        assertThat(messageMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.pcdoctor.entity.AppointmentMessage>()
                .eq(com.pcdoctor.entity.AppointmentMessage::getAppointmentId, a1))).isZero();
    }

    // ---------- 分类 / 举报 ----------

    @Test
    @DisplayName("批量删分类：被预约引用的记 2003，其余成功")
    void batchDeleteCategories() throws Exception {
        String admin = adminToken();
        long usedId = createCategory(admin, "批量测试-在用");
        long freeId = createCategory(admin, "批量测试-空闲");
        submitAppointment(register(nextUserId("bdc")), "uses category", usedId);

        String body = "{\"ids\":[" + usedId + "," + freeId + "]}";
        String json = postBatch("/api/admin/categories/batch/delete", admin, body, 0);

        var data = objectMapper.readTree(json).at("/data");
        assertThat(data.at("/success").asLong()).isEqualTo(1);
        assertThat(data.at("/failures/0/id").asString()).isEqualTo(String.valueOf(usedId));
        assertThat(data.at("/failures/0/reason").asString()).contains("存在预约");
    }

    @Test
    @DisplayName("批量标记举报：待处理的成功，已处理的记 2003（非幂等）")
    void batchHandleReports() throws Exception {
        String admin = adminToken();
        String owner = register(nextUserId("br-o"));
        // 同一人对同一目标只能举报一次，两个举报得用两个举报人
        String reporterA = register(nextUserId("br-a"));
        String reporterB = register(nextUserId("br-b"));
        long appt = submitAppointment(owner, "reported", 1L);
        fileReport(reporterA, appt, "举报一");
        fileReport(reporterB, appt, "举报二");
        long r1 = reportIdOf(admin, userIdOf(reporterA), "举报一");
        long r2 = reportIdOf(admin, userIdOf(reporterB), "举报二");
        // 先把 r2 单独处理掉，制造"已处理"状态
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/admin/reports/" + r2 + "/handle")
                .header("Authorization", "Bearer " + admin));

        String body = "{\"ids\":[" + r1 + "," + r2 + "]}";
        String json = postBatch("/api/admin/reports/batch/handle", admin, body, 0);

        var data = objectMapper.readTree(json).at("/data");
        assertThat(data.at("/success").asLong()).isEqualTo(1);
        assertThat(data.at("/failures/0/id").asString()).isEqualTo(String.valueOf(r2));
        assertThat(data.at("/failures/0/reason").asString()).contains("已处理");
    }

    @Test
    @DisplayName("批量删预约：重复 id 去重后只算一项（total 按去重后计）")
    void batchDeduplicatesIds() throws Exception {
        String admin = adminToken();
        long id = submitAppointment(register(nextUserId("dd")), "dup", 1L);

        String json = postBatch("/api/admin/appointments/batch/delete", admin,
                "{\"ids\":[" + id + "," + id + "]}", 0);

        var data = objectMapper.readTree(json).at("/data");
        assertThat(data.at("/total").asLong()).isEqualTo(1);
        assertThat(data.at("/success").asLong()).isEqualTo(1);
    }

    private long createCategory(String admin, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
    }
}
