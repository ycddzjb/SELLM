-- 机构
MERGE INTO organization (id, name, region) KEY(id) VALUES (1, '阳光小学', '南京');

-- CARS 量表三件套(scale 主键 scale_id;item/band 用固定 id 保证幂等)
MERGE INTO scale (scale_id, name, version) KEY(scale_id) VALUES ('cars', 'CARS', 'v1');
MERGE INTO scale_item (id, scale_id, item_id, stem, dimension) KEY(id)
    VALUES (1, 'cars', 'q1', '与人交往', '社交');
MERGE INTO scale_item (id, scale_id, item_id, stem, dimension) KEY(id)
    VALUES (2, 'cars', 'q2', '语言沟通', '沟通');
MERGE INTO score_band (id, scale_id, lower_bound, upper_bound, label, interpretation) KEY(id)
    VALUES (1, 'cars', 0, 3, '正常', '未见明显异常');
MERGE INTO score_band (id, scale_id, lower_bound, upper_bound, label, interpretation) KEY(id)
    VALUES (2, 'cars', 4, 7, '轻-中度', '建议进一步评估');
