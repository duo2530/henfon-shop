-- 身份与权限基础表
-- 命名规范：系统模块使用 sys_ 前缀，会员模块使用 member_ 前缀。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户/组织ID，默认平台组织',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT '密码哈希',
    real_name VARCHAR(64) NOT NULL COMMENT '真实姓名',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    dept_id BIGINT UNSIGNED DEFAULT NULL COMMENT '所属部门ID',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    user_type VARCHAR(32) NOT NULL DEFAULT 'ADMIN' COMMENT '用户类型',
    last_login_at DATETIME(3) DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip VARCHAR(64) DEFAULT NULL COMMENT '最后登录IP',
    password_updated_at DATETIME(3) DEFAULT NULL COMMENT '密码更新时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_tenant_username (tenant_id, username, is_deleted),
    UNIQUE KEY uk_sys_user_tenant_phone (tenant_id, phone, is_deleted),
    UNIQUE KEY uk_sys_user_tenant_email (tenant_id, email, is_deleted),
    KEY idx_sys_user_dept_status (dept_id, status),
    KEY idx_sys_user_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户/组织ID',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父部门ID，根部门为0',
    dept_name VARCHAR(64) NOT NULL COMMENT '部门名称',
    dept_code VARCHAR(64) NOT NULL COMMENT '部门编码',
    leader_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '负责人用户ID',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dept_tenant_code (tenant_id, dept_code, is_deleted),
    KEY idx_sys_dept_parent_sort (parent_id, sort_no),
    KEY idx_sys_dept_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统部门表';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户/组织ID',
    role_key VARCHAR(64) NOT NULL COMMENT '角色标识',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    role_sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL、CUSTOM、DEPT、DEPT_AND_SUB、SELF',
    description VARCHAR(500) DEFAULT NULL COMMENT '角色描述',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_tenant_key (tenant_id, role_key, is_deleted),
    KEY idx_sys_role_status_sort (status, role_sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父菜单ID，根节点为0',
    menu_name VARCHAR(64) NOT NULL COMMENT '菜单名称',
    menu_type VARCHAR(16) NOT NULL COMMENT '菜单类型：DIRECTORY、MENU、BUTTON',
    route_path VARCHAR(255) DEFAULT NULL COMMENT '路由路径',
    component VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
    icon VARCHAR(128) DEFAULT NULL COMMENT '菜单图标',
    permission_code VARCHAR(128) DEFAULT NULL COMMENT '权限标识',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    visible TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否可见：1是，0否',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    keep_alive TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否缓存页面：1是，0否',
    external_url VARCHAR(512) DEFAULT NULL COMMENT '外部链接',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_menu_permission (permission_code, is_deleted),
    KEY idx_sys_menu_parent_sort (parent_id, sort_no),
    KEY idx_sys_menu_type_status (menu_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单与按钮权限表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT UNSIGNED NOT NULL COMMENT '系统用户ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    menu_id BIGINT UNSIGNED NOT NULL COMMENT '菜单或按钮ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_sys_role_menu_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色菜单权限关联表';

CREATE TABLE IF NOT EXISTS sys_data_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户/组织ID',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    module_key VARCHAR(64) NOT NULL COMMENT '业务模块标识',
    scope_type VARCHAR(32) NOT NULL COMMENT '范围类型：ALL、DEPT_AND_SUB、DEPT、SELF、CUSTOM',
    custom_dept_ids JSON DEFAULT NULL COMMENT '自定义部门ID列表',
    field_masks JSON DEFAULT NULL COMMENT '字段脱敏配置',
    filter_expression TEXT DEFAULT NULL COMMENT '行级过滤表达式',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_sys_data_rule_module_status (module_key, status),
    KEY idx_sys_data_rule_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统数据权限规则表';

CREATE TABLE IF NOT EXISTS sys_role_data_rule (
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    rule_id BIGINT UNSIGNED NOT NULL COMMENT '数据规则ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (role_id, rule_id),
    KEY idx_sys_role_data_rule_rule_id (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色数据权限规则关联表';

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '系统用户ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    login_status TINYINT UNSIGNED NOT NULL COMMENT '登录结果：1成功，0失败',
    login_ip VARCHAR(64) DEFAULT NULL COMMENT '登录IP',
    user_agent VARCHAR(1000) DEFAULT NULL COMMENT '客户端信息',
    failure_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
    login_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录时间',
    PRIMARY KEY (id),
    KEY idx_sys_login_log_user_time (user_id, login_at),
    KEY idx_sys_login_log_username_time (username, login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统登录日志表';

CREATE TABLE IF NOT EXISTS sys_oper_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '请求链路ID',
    user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '操作用户ID',
    username VARCHAR(64) DEFAULT NULL COMMENT '操作用户名',
    module_key VARCHAR(64) DEFAULT NULL COMMENT '业务模块标识',
    operation VARCHAR(128) DEFAULT NULL COMMENT '操作描述',
    request_method VARCHAR(16) DEFAULT NULL COMMENT '请求方法',
    request_uri VARCHAR(512) DEFAULT NULL COMMENT '请求地址',
    request_params JSON DEFAULT NULL COMMENT '请求参数',
    response_status INT DEFAULT NULL COMMENT '响应状态码',
    client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    duration_ms BIGINT UNSIGNED DEFAULT NULL COMMENT '耗时毫秒',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_sys_oper_log_user_time (user_id, created_at),
    KEY idx_sys_oper_log_module_time (module_key, created_at),
    KEY idx_sys_oper_log_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作审计日志表';

CREATE TABLE IF NOT EXISTS member_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户/组织ID',
    member_no VARCHAR(64) NOT NULL COMMENT '会员编号',
    username VARCHAR(64) DEFAULT NULL COMMENT '登录用户名',
    password_hash VARCHAR(100) DEFAULT NULL COMMENT '密码哈希',
    nickname VARCHAR(64) NOT NULL COMMENT '会员昵称',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    member_level VARCHAR(32) NOT NULL DEFAULT 'REGULAR' COMMENT '会员等级',
    points BIGINT NOT NULL DEFAULT 0 COMMENT '积分余额',
    balance DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '账户余额',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常，0冻结',
    registered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间',
    last_login_at DATETIME(3) DEFAULT NULL COMMENT '最后登录时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_user_tenant_no (tenant_id, member_no, is_deleted),
    UNIQUE KEY uk_member_user_tenant_username (tenant_id, username, is_deleted),
    UNIQUE KEY uk_member_user_tenant_phone (tenant_id, phone, is_deleted),
    UNIQUE KEY uk_member_user_tenant_email (tenant_id, email, is_deleted),
    KEY idx_member_user_level_status (member_level, status),
    KEY idx_member_user_registered_at (registered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城会员表';
