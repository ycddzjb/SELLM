-- ASD 助手持久化建表脚本(H2 与 MySQL 8 兼容子集)
-- 各持久化任务在此追加 CREATE TABLE。使用 IF NOT EXISTS 保证可重复执行。

-- 占位语句:Spring 的脚本初始化器在剥离注释后要求脚本非空,
-- 否则报 'script' must not be null or empty。待 Task 5 追加首个真实建表语句后可移除。
SELECT 1;

-- (Task 5 追加 child 表)
-- (Task 6 追加 knowledge_doc 表)
-- (Task 7 追加 scale / scale_item / score_band 表)
