import React from 'react';
import { Lock } from 'lucide-react';
import { useAdmin } from '../../context/AdminContext';

interface PermissionGateProps {
  permission: string;
  children: React.ReactNode;
  /** 无权限时默认隐藏，也可传入占位内容用于字段脱敏/禁用态展示。 */
  fallback?: React.ReactNode;
}

/**
 * 根据服务端返回的按钮权限控制 UI 展示。
 * 无权限默认不渲染，避免仅依赖前端路由导致越权操作入口暴露。
 */
export const PermissionGate: React.FC<PermissionGateProps> = ({ permission, children, fallback = null }) => {
  const { hasPermission } = useAdmin();
  return hasPermission(permission) ? <>{children}</> : <>{fallback}</>;
};

interface PermissionDeniedProps {
  title?: string;
  description?: string;
}

/** 页面或字段无权限时的统一可理解提示。 */
export const PermissionDenied: React.FC<PermissionDeniedProps> = ({
  title = '暂无访问权限',
  description = '当前账号未被授予该功能权限，请联系系统管理员。'
}) => (
  <div className="min-h-[260px] flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center">
    <Lock className="mb-3 h-8 w-8 text-slate-400" aria-hidden="true" />
    <h2 className="text-base font-semibold text-slate-700">{title}</h2>
    <p className="mt-1 text-sm text-slate-500">{description}</p>
  </div>
);

