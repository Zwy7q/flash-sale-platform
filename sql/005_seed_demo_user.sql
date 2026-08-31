-- 选择项目数据库。
USE flash_sale;

-- 插入演示用户。
-- password_hash 暂时使用占位文本；真正注册功能中会使用 BCrypt 加密。
INSERT INTO app_user (username, password_hash, nickname, status)
SELECT 'demo_user', 'not-a-real-password-hash', '演示用户', 1
WHERE NOT EXISTS (
    SELECT 1 FROM app_user WHERE username = 'demo_user'
);
