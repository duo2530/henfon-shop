import React from 'react';
import { AuditLogPanel } from './AuditLogPanel';

/**
 * 登录记录管理页面。
 *
 * @author Henfon
 * @date 2026-09-01
 * @description 独立承载登录成功、失败记录查询，权限由 system:audit:login 控制。
 */
export const LoginLogManagementView: React.FC = () => <AuditLogPanel mode="login" />;
