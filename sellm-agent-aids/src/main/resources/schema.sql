CREATE TABLE IF NOT EXISTS teaching_aid (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    disorder_types TEXT,
    category VARCHAR(128),
    usage_guide TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS generated_asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    prompt TEXT,
    storage_key VARCHAR(512),
    task_id VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 教具推荐种子(MERGE 幂等;dev/test 每次启动执行)
MERGE INTO teaching_aid (id, name, disorder_types, category, usage_guide) KEY (id) VALUES
    (1, '视觉时间表卡片', '["ASD","ADHD"]', '视觉支持', '用图卡呈现一日流程,降低自闭症儿童对转换的焦虑,增强可预期性。'),
    (2, '情绪温度计', '["ASD","EMOTIONAL"]', '情绪管理', '将情绪强度可视化为 1-5 级,帮助儿童识别与表达情绪状态。'),
    (3, '沙盘游戏组件', '["ASD","EMOTIONAL","INTELLECTUAL"]', '游戏治疗', '通过沙盘自由摆放进行非语言表达与投射,适用于情绪与社交干预。'),
    (4, '感统训练平衡板', '["ASD","ADHD","PHYSICAL"]', '感觉统合', '用于前庭觉与本体觉训练,改善注意力与身体协调。'),
    (5, 'PECS 图片交换卡', '["ASD","SPEECH"]', '沟通辅助', '图片交换沟通系统,支持无口语或低口语儿童发起请求与表达需求。'),
    (6, '数字配对操作板', '["INTELLECTUAL","ADHD"]', '认知训练', '通过实物配对建立数量与符号对应,适合智力障碍儿童的早期数概念。');
