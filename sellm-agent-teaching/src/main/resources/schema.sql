CREATE TABLE IF NOT EXISTS lesson_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    child_id BIGINT,
    class_id BIGINT,
    source_iep_id BIGINT,
    scene VARCHAR(16),
    mode VARCHAR(16),
    disorder_type VARCHAR(32),
    ai_draft TEXT,
    content TEXT,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS courseware (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    lesson_plan_id BIGINT NOT NULL,
    disorder_type VARCHAR(32),
    ai_draft TEXT,
    content TEXT,
    storage_key VARCHAR(255),
    format VARCHAR(16),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
