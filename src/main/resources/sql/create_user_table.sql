-- 创建用户表
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID(UUID)',
    username VARCHAR(64) NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    nickname VARCHAR(64) NOT NULL COMMENT '昵称',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    avatar VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用,0禁用',
    role VARCHAR(32) NOT NULL DEFAULT 'user' COMMENT '角色:user/admin',
    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted INTEGER DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_user_id (user_id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email),
    UNIQUE KEY uk_user_phone (phone),
    KEY idx_user_status (status),
    KEY idx_user_role (role),
    KEY idx_user_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 初始化管理员（当前后端使用 MD5 校验）
INSERT INTO t_user
(id, user_id, username, password_hash, nickname, role, status, create_time, update_time, deleted)
VALUES
(1, '00000000-0000-0000-0000-000000000001', 'admin', '0192023a7bbd73250516f069df18b500', '系统管理员', 'admin', 1, NOW(), NOW(), 0);
