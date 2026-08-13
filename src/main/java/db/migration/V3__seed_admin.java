package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 种子管理员账号：密码经 Flyway 占位符注入（application.yml 的
 * spring.flyway.placeholders ← ADMIN_INITIAL_PASSWORD 环境变量/.env），
 * 迁移时 bcrypt 落库，明文/哈希都不进迁移脚本。
 * 未设置则跳过创建（避免产出无法登录的弱账号），生产部署必须设置。
 */
public class V3__seed_admin extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V3__seed_admin.class);

    @Override
    public void migrate(Context context) throws Exception {
        var placeholders = context.getConfiguration().getPlaceholders();
        String username = placeholder(placeholders, "admin_username", "admin");
        String displayName = placeholder(placeholders, "admin_display_name", "管理员");
        String password = placeholder(placeholders, "admin_initial_password", "");
        if (password.isBlank()) {
            log.warn("未设置 ADMIN_INITIAL_PASSWORD，跳过管理员种子账号创建（如需管理员请设置后重建库或手动插入）");
            return;
        }

        try (PreparedStatement check = context.getConnection()
                .prepareStatement("SELECT 1 FROM user_table WHERE user_id = ?")) {
            check.setString(1, username);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    log.warn("用户 {} 已存在，跳过管理员种子创建", username);
                    return;
                }
            }
        }

        String hash = new BCryptPasswordEncoder().encode(password);
        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "INSERT INTO user_table (user_id, user_name, sex, password_hash, level, must_change_password, contact_details) "
                        + "VALUES (?, ?, 'unknown', ?, 2, 0, '')")) {
            ps.setString(1, username);
            ps.setString(2, displayName);
            ps.setString(3, hash);
            ps.executeUpdate();
        }
        log.info("管理员种子账号 {} 已创建", username);
    }

    private String placeholder(java.util.Map<String, String> placeholders, String key, String fallback) {
        String value = placeholders.get(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}