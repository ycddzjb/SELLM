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
-- (阶段 C:审核通过建档案时儿童姓名可能暂缺,放开 name_enc 非空约束)
ALTER TABLE child ALTER COLUMN name_enc DROP NOT NULL;
ALTER TABLE child ALTER COLUMN disorder_type DROP NOT NULL;
-- (阶段 D 批一:儿童档案扩展字段,非 PII 概要文本明文存)
ALTER TABLE child ADD COLUMN IF NOT EXISTS baseline_summary VARCHAR(1024);
ALTER TABLE child ADD COLUMN IF NOT EXISTS annual_iep_summary VARCHAR(1024);
ALTER TABLE child ADD COLUMN IF NOT EXISTS monthly_goal VARCHAR(1024);
ALTER TABLE child ADD COLUMN IF NOT EXISTS reassess_date DATE;
ALTER TABLE child ADD COLUMN IF NOT EXISTS iep_due_date DATE;
ALTER TABLE child ADD COLUMN IF NOT EXISTS intervention_progress VARCHAR(128);

-- (阶段 D 批一:儿童成长记录,单表 + log_type 区分课堂追踪/家校沟通/阶段复盘)
CREATE TABLE IF NOT EXISTS child_log (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    child_id       BIGINT NOT NULL,
    log_type       VARCHAR(32) NOT NULL,
    content        VARCHAR(2048),
    author_user_id BIGINT,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- (阶段 E:家长家庭 IEP,家长设目标→大模型按最新定稿报告出家庭训练计划)
CREATE TABLE IF NOT EXISTS family_iep (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    child_id          BIGINT NOT NULL,
    parent_user_id    BIGINT NOT NULL,
    parent_goal       VARCHAR(1024),
    draft             VARCHAR(8192),
    finalized_content VARCHAR(8192),
    status            VARCHAR(16) NOT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- (阶段 F:评估媒体,多模态素材上传记录;对象本体存对象存储,库里只存 object_key)
CREATE TABLE IF NOT EXISTS evaluation_media (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    child_id         BIGINT NOT NULL,
    scale_id         VARCHAR(64),
    media_type       VARCHAR(16) NOT NULL,   -- IMAGE / VIDEO / NOTE
    object_key       VARCHAR(256),           -- 对象存储 key;NOTE 类型可空
    note_text        VARCHAR(2048),
    uploader_user_id BIGINT,
    status           VARCHAR(16) NOT NULL,   -- UPLOADED / ANALYZED
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

-- (阶段 B 追加 scale/scale_item 字段:量表品类/简介、题目排序/每题最高分)
ALTER TABLE scale ADD COLUMN IF NOT EXISTS disorder_type VARCHAR(32);
ALTER TABLE scale ADD COLUMN IF NOT EXISTS description VARCHAR(512);
ALTER TABLE scale_item ADD COLUMN IF NOT EXISTS sort_order INT DEFAULT 0;
ALTER TABLE scale_item ADD COLUMN IF NOT EXISTS max_score DOUBLE DEFAULT 4;

-- (Task 3 追加 organization 表)
CREATE TABLE IF NOT EXISTS organization (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    name    VARCHAR(128) NOT NULL,
    region  VARCHAR(128)
);
-- (计划六 Task 2 追加 organization 字段:障碍类型多选/省/市)
ALTER TABLE organization ADD COLUMN IF NOT EXISTS disorder_types VARCHAR(256);
ALTER TABLE organization ADD COLUMN IF NOT EXISTS province VARCHAR(64);
ALTER TABLE organization ADD COLUMN IF NOT EXISTS city VARCHAR(64);

-- (Task 3 追加 app_user 表,表名 app_user 避开 SQL 保留字 user)
CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    org_id        BIGINT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- (计划五 Task 1 追加 app_user.status 列:账号状态 ACTIVE/PENDING/REJECTED,登录校验用)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

-- (P9 微信登录:绑定微信 openid,家长端 code2session 静默登录用;唯一,可空)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS wx_openid VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_wx_openid ON app_user (wx_openid);

-- (计划六 Task 4 追加 class_room 表:班级,表名用 class_room 避开 SQL 保留字 class)
CREATE TABLE IF NOT EXISTS class_room (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(128) NOT NULL,
    org_id         BIGINT NOT NULL,
    disorder_types VARCHAR(256),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- (计划六 Task 5 追加 teacher_class 关联表:老师多对多绑定班级)
CREATE TABLE IF NOT EXISTS teacher_class (
    teacher_user_id BIGINT NOT NULL,
    class_id        BIGINT NOT NULL,
    PRIMARY KEY (teacher_user_id, class_id)
);

-- (阶段 C 追加 parent_profile 表:家长扩展信息 + 待建儿童暂存,姓名加密)
CREATE TABLE IF NOT EXISTS parent_profile (
    user_id              BIGINT PRIMARY KEY,
    name_enc             VARCHAR(512),
    relationship         VARCHAR(32),
    assigned_teacher_id  BIGINT,
    child_name_enc       VARCHAR(512),
    child_disorder_type  VARCHAR(64),
    class_id             BIGINT,
    child_id             BIGINT,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

-- (Task 10 追加 iep 表:IEP 记录落库)
CREATE TABLE IF NOT EXISTS iep (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id     BIGINT NOT NULL,
    child_id      BIGINT NOT NULL,
    draft         VARCHAR(8000) NOT NULL,
    finalized_content VARCHAR(8000),
    status        VARCHAR(16) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
