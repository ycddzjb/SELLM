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
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_id    VARCHAR(64)  NOT NULL,
    content   VARCHAR(4000) NOT NULL,
    source    VARCHAR(256),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- (Task 7 追加 scale / scale_item / score_band 表)
CREATE TABLE IF NOT EXISTS scale (
    scale_id  VARCHAR(64) PRIMARY KEY,
    name      VARCHAR(128) NOT NULL,
    version   VARCHAR(32)  NOT NULL
);

CREATE TABLE IF NOT EXISTS scale_item (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    scale_id  VARCHAR(64) NOT NULL,
    item_id   VARCHAR(64) NOT NULL,
    stem      VARCHAR(512) NOT NULL,
    dimension VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS score_band (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    scale_id       VARCHAR(64) NOT NULL,
    lower_bound    DOUBLE NOT NULL,
    upper_bound    DOUBLE NOT NULL,
    label          VARCHAR(128) NOT NULL,
    interpretation VARCHAR(512)
);
