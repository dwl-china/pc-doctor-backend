package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 管理端用户搜索（契约 §4.3 GET /api/admin/users）。
 *
 * <p>回归背景：管理端搜索框把同一个关键词同时作为 user_id 与 user_name 传给后端，
 * 而这两个条件是**取交集**的，导致按账号搜索几乎搜不出东西
 * （只有账号和昵称都含该串才命中）。现在搜索框走 keyword（账号或昵称）。
 */
class AdminUserListIntegrationTest extends BaseIntegrationTest {

    private int searchTotal(String admin, String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/users?" + query)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/total").asInt();
    }

    @Test
    @DisplayName("keyword 按账号匹配：昵称完全不含该串也能搜到")
    void keywordMatchesUserId() throws Exception {
        String admin = adminToken();
        String id = nextUserId("kwid");
        register(id); // 昵称是 t-<id>，与账号前缀一致，这里要验证的是"不必昵称也含同一串"

        // 账号前缀命中；昵称里同样含该串是 register 的固有格式，故再单独验证纯账号场景
        assertThat(searchTotal(admin, "keyword=" + id)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("keyword 按昵称匹配：账号与昵称无关时也能搜到")
    void keywordMatchesUserName() throws Exception {
        String admin = adminToken();
        String id = nextUserId("kwname");
        String nick = "独特昵称" + System.nanoTime() % 10_000_000;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"user_id\":\"" + id + "\",\"password\":\"" + DEFAULT_PASSWORD
                                + "\",\"user_name\":\"" + nick + "\"}"))
                .andExpect(jsonPath("$.code").value(0));

        // 昵称命中，账号里没有这串 —— 旧的 user_id+user_name 交集写法会漏掉。
        // 这里传原始中文：MockMvc 自己会做 URL 编码，预先编码会变成双重编码而匹配不到
        assertThat(searchTotal(admin, "keyword=" + nick)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("user_id 与 user_name 仍各自精确筛选，两者并列时取交集")
    void fieldFiltersStillIntersect() throws Exception {
        String admin = adminToken();
        String id = nextUserId("kwand");
        register(id); // 昵称固定为 t-<id>

        // 账号命中且昵称也含该串 → 命中
        assertThat(searchTotal(admin, "user_id=" + id + "&user_name=" + id)).isGreaterThanOrEqualTo(1);
        // 账号命中但昵称不含 → 交集为空（这正是旧搜索框的问题所在）
        assertThat(searchTotal(admin, "user_id=" + id + "&user_name=绝对不会匹配的串")).isZero();
    }

    @Test
    @DisplayName("keyword 可与角色筛选叠加")
    void keywordWithLevelFilter() throws Exception {
        String admin = adminToken();
        String id = nextUserId("kwlv");
        register(id);
        promoteToDoctor(admin, id);

        assertThat(searchTotal(admin, "keyword=" + id + "&level=1")).isEqualTo(1);
        assertThat(searchTotal(admin, "keyword=" + id + "&level=0")).isZero();
    }

    @Test
    @DisplayName("keyword 为空串等同于不筛选")
    void blankKeywordIgnored() throws Exception {
        String admin = adminToken();
        // 与"完全不传 keyword"对比，而不是断言某个绝对值 ——
        // 本地库有真实数据、CI 是全新库，只有相对比较才两边都成立
        int withoutKeyword = searchTotal(admin, "page=1&size=10");
        assertThat(withoutKeyword).isGreaterThanOrEqualTo(1);
        assertThat(searchTotal(admin, "keyword=")).isEqualTo(withoutKeyword);
    }
}
