package com.example.flashsale.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层，负责从 app_user 表查询用户。
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据用户 ID 查询用户。
     * 找不到用户时返回 Optional.empty()，而不是返回 null。
     */
    public Optional<AppUser> findById(Long id) {
        String sql = """
                SELECT id, username, password_hash, nickname, status,
                       created_at, updated_at
                FROM app_user
                WHERE id = ?
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            AppUser user = new AppUser();
            user.setId(resultSet.getLong("id"));
            user.setUsername(resultSet.getString("username"));
            user.setPasswordHash(resultSet.getString("password_hash"));
            user.setNickname(resultSet.getString("nickname"));
            user.setStatus(resultSet.getInt("status"));
            user.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
            user.setUpdatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime());
            return user;
        }, id).stream().findFirst();
    }
}
