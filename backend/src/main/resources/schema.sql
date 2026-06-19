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
-- (Task 7 追加 child.guardian_user_id 列:关联家长账号,行级权限用)
ALTER TABLE child ADD COLUMN IF NOT EXISTS guardian_user_id BIGINT;
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

-- (Task 3 追加 organization 表)
CREATE TABLE IF NOT EXISTS organization (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    name    VARCHAR(128) NOT NULL,
    region  VARCHAR(128)
);

-- (Task 3 追加 app_user 表,表名 app_user 避开 SQL 保留字 user)
CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    org_id        BIGINT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- (Task 8 追加 assessment 表:评估记录落库)
CREATE TABLE IF NOT EXISTS assessment (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    child_id     BIGINT NOT NULL,
    scale_id     VARCHAR(64) NOT NULL,
    total_score  DOUBLE NOT NULL,
    band_label   VARCHAR(128) NOT NULL,
    interpretation VARCHAR(512),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- (Task 9 追加 report 表:报告记录落库)
CREATE TABLE IF NOT EXISTS report (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    child_id      BIGINT NOT NULL,
    draft         VARCHAR(8000) NOT NULL,
    finalized_content VARCHAR(8000),
    status        VARCHAR(16) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
