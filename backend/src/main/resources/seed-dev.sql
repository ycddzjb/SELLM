-- 机构
MERGE INTO organization (id, name, region) KEY(id) VALUES (1, '阳光小学', '南京');

-- CARS 量表三件套(scale 主键 scale_id;item/band 用固定 id 保证幂等)
MERGE INTO scale (scale_id, name, version, disorder_type, description) KEY(scale_id)
    VALUES ('cars', 'CARS', 'v1', 'ASD', '儿童孤独症评定量表');
MERGE INTO scale_item (id, scale_id, item_id, stem, dimension, sort_order, max_score) KEY(id)
    VALUES (1, 'cars', 'q1', '与人交往', '社交', 1, 4);
MERGE INTO scale_item (id, scale_id, item_id, stem, dimension, sort_order, max_score) KEY(id)
    VALUES (2, 'cars', 'q2', '语言沟通', '沟通', 2, 4);
MERGE INTO score_band (id, scale_id, lower_bound, upper_bound, label, interpretation) KEY(id)
    VALUES (1, 'cars', 0, 3, '正常', '未见明显异常');
MERGE INTO score_band (id, scale_id, lower_bound, upper_bound, label, interpretation) KEY(id)
    VALUES (2, 'cars', 4, 7, '轻-中度', '建议进一步评估');

-- 知识库语料(category:SCALE_SYSTEM 量表体系 / IEP_CASE 个案 / POLICY_ETHICS 政策伦理)
-- 用固定 id 保证幂等(MERGE KEY(id))。供 RAG 召回拼诊断/IEP prompt。
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (1, 'kb-scale-001', 'SCALE_SYSTEM', 'CARS 儿童孤独症评定量表从人际关系、模仿、情感反应、躯体运用、与非生命物体关系、对环境变化适应、视觉反应、听觉反应、语言沟通等15个维度评定,每项1-4分,总分用于判定孤独症严重程度。', 'CARS 手册');
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (2, 'kb-scale-002', 'SCALE_SYSTEM', '能力维度等级常分为:重度缺陷、中度缺陷、轻度缺陷、接近正常、正常。诊断应按动作、语言沟通、社会交往、认知理解、生活自理等维度分别给出能力等级与现存障碍。', '特殊教育评估规范');
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (3, 'kb-scale-003', 'SCALE_SYSTEM', '精细动作评估常用指标:剥珠/穿珠正确率、抓握方式、手眼协调;社交评估常用指标:眼神接触频率与持续时间、共同注意、轮流互动应答率。', '康复评估指标库');
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (4, 'kb-iep-001', 'IEP_CASE', '个案:7岁ASD儿童,精细动作中度缺陷(剥珠正确率40%)。IEP动作领域长期目标:提升手部精细控制;短期目标:8周内剥珠正确率达70%。训练方式:一对一桌面操作;频次:每日2次每次15分钟;步骤:示范-辅助-独立递减提示,从大珠到小珠分级。', 'IEP个案库');
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (5, 'kb-iep-002', 'IEP_CASE', '个案:语言沟通轻度缺陷儿童。语言训练目标:扩展双词短语表达。训练方式:自然情境教学+图片交换;频次:每日嵌入3个生活场景;步骤:延迟满足诱发需求-示范目标语-即时强化。社交互动:用结构化游戏增加眼神接触与轮流。', 'IEP个案库');
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (6, 'kb-iep-003', 'IEP_CASE', '生活自理训练个案:如厕/穿衣分步任务分析,采用前向链锁+视觉提示卡;认知培养:配对分类、因果理解,用实物与情境泛化。每阶段设可量化达标标准并定期复评。', 'IEP个案库');
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (7, 'kb-policy-001', 'POLICY_ETHICS', '《特殊教育提升计划》强调融合教育、随班就读、个别化教育计划(IEP),保障残疾儿童受教育权,反对任何形式的歧视与隔离。干预须以儿童最大利益为本。', '特殊教育提升计划');
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (8, 'kb-policy-002', 'POLICY_ETHICS', '康复干预伦理底线:严禁体罚、厌恶疗法、束缚、电击、禁食等伤害性手段;不得使用未经验证或有害的疗法;须取得监护人知情同意;保护儿童隐私与人格尊严;干预以正向行为支持为原则。', '康复伦理准则');
MERGE INTO knowledge_doc (id, doc_id, category, content, source) KEY(id) VALUES
 (9, 'kb-policy-003', 'POLICY_ETHICS', 'IEP制定须多学科团队与家长共同参与,目标具体可测可达成(SMART),定期评估调整;避免超出儿童能力的不合理目标,避免标签化与歧视性表述。', '个别化教育计划指南');
