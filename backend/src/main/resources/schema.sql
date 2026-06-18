-- ASD 助手持久化建表脚本(H2 与 MySQL 8 兼容子集)
-- 各持久化任务在此追加 CREATE TABLE。使用 IF NOT EXISTS 保证可重复执行。

-- (Task 5 追加 child 表)
CREATE TABLE IF NOT EXISTS child (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name_enc     VARCHAR(512) NOT NULL,
    disorder_type VARCHAR(64) NOT NULL,
    org_id       BIGINT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- (Task 6 追加 knowledge_doc 表)
-- (Task 7 追加 scale / scale_item / score_band 表)
