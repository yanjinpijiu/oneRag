-- 创建对话表
CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGINT NOT NULL COMMENT '主键ID',
    conversation_id VARCHAR(36) NOT NULL COMMENT '对话ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    title VARCHAR(255) NOT NULL COMMENT '对话标题',
    last_time DATETIME NOT NULL COMMENT '最后时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted INTEGER DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_id (conversation_id),
    KEY idx_user_id (user_id),
    KEY idx_last_time (last_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话表';

-- 创建对话消息表
CREATE TABLE IF NOT EXISTS t_conversation_message (
    id BIGINT NOT NULL COMMENT '主键ID',
    conversation_id VARCHAR(36) NOT NULL COMMENT '对话ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user/assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted INTEGER DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (id),
    KEY idx_conversation_id (conversation_id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';
