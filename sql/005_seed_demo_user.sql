-- 选择项目数据库。
USE flash_sale;

-- 插入演示用户。
-- password_hash 是“password”的 BCrypt 密文，仅用于本地演示。
INSERT INTO app_user (username, password_hash, nickname, status)
VALUES ('demo_user',
        '$2a$10$48iVApN1ypPP4uMw6zAQFu0OivdalWw2n1nsii4XXGF4MWrWGFJVa',
        '演示用户',
        1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    nickname = VALUES(nickname),
    status = VALUES(status);

SELECT username, password_hash, status
FROM app_user
WHERE username = 'demo_user';


USE flash_sale;

UPDATE app_user
SET password_hash = '$2a$10$48iVApN1ypPP4uMw6zAQFu0OivdalWw2n1nsii4XXGF4MWrWGFJVa',
    status = 1
WHERE username = 'demo_user';