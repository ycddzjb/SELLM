DELETE FROM score_band;
DELETE FROM scale_item;
DELETE FROM scale;
INSERT INTO scale (scale_id, name, version, disorder_type, description) VALUES ('cars', 'CARS', 'v1', 'ASD', '儿童孤独症评定量表');
INSERT INTO scale_item (scale_id, item_id, stem, dimension, sort_order, max_score) VALUES ('cars', 'q1', '社交', '社交', 1, 4);
INSERT INTO scale_item (scale_id, item_id, stem, dimension, sort_order, max_score) VALUES ('cars', 'q2', '沟通', '沟通', 2, 4);
INSERT INTO score_band (scale_id, lower_bound, upper_bound, label, interpretation)
    VALUES ('cars', 0, 3, '正常', '未见明显异常');
INSERT INTO score_band (scale_id, lower_bound, upper_bound, label, interpretation)
    VALUES ('cars', 4, 7, '轻-中度', '建议进一步评估');
