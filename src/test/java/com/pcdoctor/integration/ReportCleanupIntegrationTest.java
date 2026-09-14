package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import com.pcdoctor.entity.Report;
import com.pcdoctor.mapper.ReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 内容被删除后举报自动了结：
 * 管理员在举报处理里删掉违规内容后，那条举报不该继续挂在「待处理」——
 * 否则再点删除只会得到「预约不存在/留言不存在」的死胡同。
 */
class ReportCleanupIntegrationTest extends BaseIntegrationTest {

    private static final int TYPE_APPOINTMENT = 1;
    private static final int TYPE_MESSAGE = 2;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_HANDLED = 1;

    @Autowired
    ReportMapper reportMapper;

    // ---------- 工具 ----------

    private String userIdOf(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString())
                .at("/data/userId").asString();
    }

    private long postMessage(String token, long appointmentId, String text) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments/" + appointmentId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"message\":\"" + text + "\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    private void report(String token, int type, long targetId, String reason) throws Exception {
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"target_type\":" + type + ",\"target_id\":" + targetId
                                + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    private Report reportOf(int type, long targetId, String reporterId) {
        return reportMapper.selectList(null).stream()
                .filter(r -> r.getTargetType() == type && r.getTargetId() == targetId
                        && r.getReporterId().equals(reporterId))
                .findFirst().orElseThrow(() -> new IllegalStateException("未找到举报记录"));
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("删除被举报的预约：该预约的举报自动置为已处理")
    void deleteAppointmentAutoHandlesReport() throws Exception {
        String admin = adminToken();
        String owner = register(nextUserId("rc-o"));
        String reporter = register(nextUserId("rc-r"));
        long appt = submitAppointment(owner, "violating appointment", 1L);
        report(reporter, TYPE_APPOINTMENT, appt, "违规内容");

        mockMvc.perform(delete("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0));

        Report r = reportOf(TYPE_APPOINTMENT, appt, userIdOf(reporter));
        assertThat(r.getStatus()).isEqualTo(STATUS_HANDLED);
        assertThat(r.getHandleTime()).isNotNull();
        assertThat(r.getHandlerId()).isEqualTo("admin");
    }

    @Test
    @DisplayName("删除被举报的留言：该留言的举报自动置为已处理")
    void deleteMessageAutoHandlesReport() throws Exception {
        String admin = adminToken();
        String owner = register(nextUserId("rm-o"));
        String reporter = register(nextUserId("rm-r"));
        long appt = submitAppointment(owner, "appointment", 1L);
        long msg = postMessage(owner, appt, "violating message");
        report(reporter, TYPE_MESSAGE, msg, "违规留言");

        mockMvc.perform(delete("/api/messages/" + msg)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0));

        Report r = reportOf(TYPE_MESSAGE, msg, userIdOf(reporter));
        assertThat(r.getStatus()).isEqualTo(STATUS_HANDLED);
    }

    @Test
    @DisplayName("删除预约连带其下留言：留言的举报也一并了结（不留下点进去报错的待处理项）")
    void deleteAppointmentAlsoHandlesItsMessageReports() throws Exception {
        String admin = adminToken();
        String owner = register(nextUserId("rc2-o"));
        String reporter = register(nextUserId("rc2-r"));
        long appt = submitAppointment(owner, "appointment with reported message", 1L);
        long msg = postMessage(owner, appt, "reported message");
        report(reporter, TYPE_MESSAGE, msg, "违规留言");

        mockMvc.perform(delete("/api/appointments/" + appt)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0));

        Report r = reportOf(TYPE_MESSAGE, msg, userIdOf(reporter));
        assertThat(r.getStatus()).isEqualTo(STATUS_HANDLED);
    }

    @Test
    @DisplayName("已处理的举报不被覆盖：处理人与处理时间保持原样")
    void alreadyHandledReportIsNotOverwritten() throws Exception {
        String admin = adminToken();
        String owner = register(nextUserId("rc3-o"));
        String reporter = register(nextUserId("rc3-r"));
        long appt = submitAppointment(owner, "already handled", 1L);
        report(reporter, TYPE_APPOINTMENT, appt, "违规内容");
        // 管理员先手动标记处理
        long reportId = reportOf(TYPE_APPOINTMENT, appt, userIdOf(reporter)).getId();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/reports/" + reportId + "/handle")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0));
        var handledAt = reportMapper.selectById(reportId).getHandleTime();

        // 随后删除预约：不能再改这条已处理的记录
        mockMvc.perform(delete("/api/appointments/" + appt)
                .header("Authorization", "Bearer " + admin));

        Report r = reportMapper.selectById(reportId);
        assertThat(r.getStatus()).isEqualTo(STATUS_HANDLED);
        assertThat(r.getHandleTime()).isEqualTo(handledAt);
    }

    @Test
    @DisplayName("只了结被删目标的举报：同类型其它目标的待处理举报不受影响")
    void unrelatedReportsUntouched() throws Exception {
        String admin = adminToken();
        String owner = register(nextUserId("rc4-o"));
        String reporter = register(nextUserId("rc4-r"));
        long doomed = submitAppointment(owner, "will be deleted", 1L);
        long untouched = submitAppointment(owner, "stays", 1L);
        report(reporter, TYPE_APPOINTMENT, doomed, "删这条");
        report(reporter, TYPE_APPOINTMENT, untouched, "留这条");

        mockMvc.perform(delete("/api/appointments/" + doomed)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(reportOf(TYPE_APPOINTMENT, doomed, userIdOf(reporter)).getStatus())
                .isEqualTo(STATUS_HANDLED);
        Report other = reportOf(TYPE_APPOINTMENT, untouched, userIdOf(reporter));
        assertThat(other.getStatus()).isEqualTo(STATUS_PENDING);
        assertThat(other.getHandleTime()).isNull();
    }

    @Test
    @DisplayName("普通用户删自己的留言：也会了结针对它的举报")
    void ownerDeletingOwnMessageAlsoHandles() throws Exception {
        String owner = register(nextUserId("rc5-o"));
        String reporter = register(nextUserId("rc5-r"));
        long appt = submitAppointment(owner, "appointment", 1L);
        long msg = postMessage(owner, appt, "self deleted");
        report(reporter, TYPE_MESSAGE, msg, "违规留言");

        mockMvc.perform(delete("/api/messages/" + msg)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(reportOf(TYPE_MESSAGE, msg, userIdOf(reporter)).getStatus())
                .isEqualTo(STATUS_HANDLED);
    }
}
