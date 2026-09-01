import React from 'react';
import { AuditLogPanel } from './AuditLogPanel';

/**
 * 操作审计管理页面。
 *
 * @author Henfon
 * @date 2026-09-01
 * @description 独立承载管理端操作请求查询，权限由 system:audit:operation 控制。
 */
export const OperationLogManagementView: React.FC = () => <AuditLogPanel mode="operation" />;
