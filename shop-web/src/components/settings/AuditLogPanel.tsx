import React, { useEffect, useState } from 'react';
import { RefreshCw, ShieldCheck } from 'lucide-react';
import { BackendLoginLog, BackendOperationLog, listLoginLogs, listOperationLogs } from '../../api/adminApi';
import { PermissionDenied } from '../common/PermissionGate';
import { useAdmin } from '../../context/AdminContext';

type AuditTab = 'operation' | 'login';

/** 管理端审计记录查询面板，数据来源于后端独立事务写入的日志表。 */
export const AuditLogPanel: React.FC = () => {
  const { showToast, hasPermission } = useAdmin();
  const [tab, setTab] = useState<AuditTab>('operation');
  const [operations, setOperations] = useState<BackendOperationLog[]>([]);
  const [logins, setLogins] = useState<BackendLoginLog[]>([]);
  const [loading, setLoading] = useState(false);

  const loadLogs = async () => {
    setLoading(true);
    try {
      if (tab === 'operation') {
        const page = await listOperationLogs({ size: 20 });
        setOperations(page.records || []);
      } else {
        const page = await listLoginLogs({ size: 20 });
        setLogins(page.records || []);
      }
    } catch (error) {
      showToast(error instanceof Error ? error.message : '审计日志加载失败', 'warning');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!hasPermission('system:audit:operation') && hasPermission('system:audit:login')) setTab('login');
  }, [hasPermission]);
  useEffect(() => { if (hasPermission(tab === 'operation' ? 'system:audit:operation' : 'system:audit:login')) void loadLogs(); }, [tab, hasPermission]);

  if (!hasPermission('system:audit:operation') && !hasPermission('system:audit:login')) return <PermissionDenied title="暂无审计日志权限" />;

  return (
    <section className="bg-white rounded-xl border border-[#E2E8F0] p-5 md:p-6 shadow-xs">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg"><ShieldCheck className="w-5 h-5" /></div>
            <div><h3 className="text-base font-semibold text-gray-900">操作审计与登录记录</h3><p className="text-xs text-gray-500">关键管理端请求由后端自动采集，支持追踪责任人与异常登录。</p></div>
          </div>
          <button type="button" onClick={() => void loadLogs()} disabled={loading} className="h-8 px-3 rounded-lg border border-gray-200 text-xs font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-50 flex items-center gap-1.5"><RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />刷新</button>
        </div>
        <div className="flex gap-4 border-b border-gray-100 mb-3">
          {hasPermission('system:audit:operation') && <button type="button" onClick={() => setTab('operation')} className={`pb-2 text-xs font-semibold border-b-2 ${tab === 'operation' ? 'text-indigo-600 border-indigo-600' : 'text-gray-500 border-transparent'}`}>操作日志</button>}
          {hasPermission('system:audit:login') && <button type="button" onClick={() => setTab('login')} className={`pb-2 text-xs font-semibold border-b-2 ${tab === 'login' ? 'text-indigo-600 border-indigo-600' : 'text-gray-500 border-transparent'}`}>登录日志</button>}
        </div>
        <div className="overflow-x-auto">
          {tab === 'operation' ? (
            <table className="w-full text-xs"><thead><tr className="text-left text-gray-500 border-b"><th className="py-2 pr-3">时间</th><th className="py-2 pr-3">用户</th><th className="py-2 pr-3">模块/操作</th><th className="py-2 pr-3">请求</th><th className="py-2">结果</th></tr></thead><tbody>{operations.map((item) => <tr key={item.id} className="border-b border-gray-50"><td className="py-2 pr-3 whitespace-nowrap">{item.createdAt || '-'}</td><td className="py-2 pr-3">{item.username || '-'}</td><td className="py-2 pr-3">{item.moduleKey || '-'} / {item.operation || '-'}</td><td className="py-2 pr-3 font-mono">{item.requestMethod} {item.requestUri}</td><td className={`py-2 font-semibold ${item.responseStatus && item.responseStatus >= 400 ? 'text-red-600' : 'text-emerald-600'}`}>{item.responseStatus || '-'}</td></tr>)}</tbody></table>
          ) : (
            <table className="w-full text-xs"><thead><tr className="text-left text-gray-500 border-b"><th className="py-2 pr-3">时间</th><th className="py-2 pr-3">用户</th><th className="py-2 pr-3">状态</th><th className="py-2 pr-3">IP</th><th className="py-2">失败原因</th></tr></thead><tbody>{logins.map((item) => <tr key={item.id} className="border-b border-gray-50"><td className="py-2 pr-3 whitespace-nowrap">{item.loginAt || '-'}</td><td className="py-2 pr-3">{item.username}</td><td className={`py-2 pr-3 font-semibold ${item.loginStatus === 1 ? 'text-emerald-600' : 'text-red-600'}`}>{item.loginStatus === 1 ? '成功' : '失败'}</td><td className="py-2 pr-3">{item.loginIp || '-'}</td><td className="py-2">{item.failureReason || '-'}</td></tr>)}</tbody></table>
          )}
          {loading && <p className="py-5 text-center text-xs text-gray-400">日志加载中...</p>}
          {!loading && ((tab === 'operation' && operations.length === 0) || (tab === 'login' && logins.length === 0)) && <p className="py-5 text-center text-xs text-gray-400">暂无日志记录</p>}
        </div>
    </section>
  );
};
