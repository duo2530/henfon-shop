import React, { FormEvent, useCallback, useEffect, useState } from 'react';
import { RefreshCw, ShieldCheck } from 'lucide-react';
import { BackendLoginLog, BackendOperationLog, BackendPage, listLoginLogs, listOperationLogs } from '../../api/adminApi';
import { PermissionDenied } from '../common/PermissionGate';
import { useAdmin } from '../../context/AdminContext';

type AuditTab = 'operation' | 'login';
type LoginStatusFilter = '' | '0' | '1';

const PAGE_SIZE = 20;

/** 管理端审计记录查询面板，数据来源于后端独立事务写入的日志表。
 *
 * @author Henfon
 * @date 2026-09-01
 * @description 提供登录与操作日志的权限隔离、条件筛选、分页和异常重试。
 */
export const AuditLogPanel: React.FC = () => {
  const { showToast, hasPermission } = useAdmin();
  const [tab, setTab] = useState<AuditTab>('operation');
  const [operationsPage, setOperationsPage] = useState<BackendPage<BackendOperationLog> | null>(null);
  const [loginsPage, setLoginsPage] = useState<BackendPage<BackendLoginLog> | null>(null);
  const [operationCurrent, setOperationCurrent] = useState(1);
  const [loginCurrent, setLoginCurrent] = useState(1);
  const [operationUsername, setOperationUsername] = useState('');
  const [operationModuleKey, setOperationModuleKey] = useState('');
  const [operationFilter, setOperationFilter] = useState({ username: '', moduleKey: '' });
  const [loginUsername, setLoginUsername] = useState('');
  const [loginStatus, setLoginStatus] = useState<LoginStatusFilter>('');
  const [loginFilter, setLoginFilter] = useState<{ username: string; status?: number }>({ username: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canViewOperation = hasPermission('system:audit:operation');
  const canViewLogin = hasPermission('system:audit:login');

  const loadLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (tab === 'operation') {
        const page = await listOperationLogs({
          current: operationCurrent,
          size: PAGE_SIZE,
          username: operationFilter.username || undefined,
          moduleKey: operationFilter.moduleKey || undefined,
        });
        setOperationsPage(page);
      } else {
        const page = await listLoginLogs({
          current: loginCurrent,
          size: PAGE_SIZE,
          username: loginFilter.username || undefined,
          status: loginFilter.status,
        });
        setLoginsPage(page);
      }
    } catch (requestError) {
      const message = requestError instanceof Error ? requestError.message : '审计日志加载失败，请稍后重试';
      setError(message);
      showToast(message, 'warning');
      // 出错时清理当前页数据，避免用户误以为旧数据仍与当前筛选条件一致。
      if (tab === 'operation') setOperationsPage(null);
      else setLoginsPage(null);
    } finally {
      setLoading(false);
    }
  }, [loginCurrent, loginFilter, operationCurrent, operationFilter, showToast, tab]);

  useEffect(() => {
    // 没有当前页权限时不发起请求，避免因切换权限导致后端 403。
    if ((tab === 'operation' && canViewOperation) || (tab === 'login' && canViewLogin)) void loadLogs();
  }, [canViewLogin, canViewOperation, loadLogs, tab]);

  useEffect(() => {
    if (!canViewOperation && canViewLogin && tab !== 'login') setTab('login');
    if (!canViewLogin && canViewOperation && tab !== 'operation') setTab('operation');
  }, [canViewLogin, canViewOperation, tab]);

  if (!canViewOperation && !canViewLogin) return <PermissionDenied title="暂无审计日志权限" />;

  const submitOperationFilter = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    // 查询条件提交后回到第一页，防止当前页超出筛选结果范围。
    setOperationCurrent(1);
    setOperationFilter({ username: operationUsername.trim(), moduleKey: operationModuleKey.trim() });
  };

  const resetOperationFilter = () => {
    setOperationUsername('');
    setOperationModuleKey('');
    setOperationCurrent(1);
    setOperationFilter({ username: '', moduleKey: '' });
  };

  const submitLoginFilter = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoginCurrent(1);
    setLoginFilter({ username: loginUsername.trim(), status: loginStatus === '' ? undefined : Number(loginStatus) });
  };

  const resetLoginFilter = () => {
    setLoginUsername('');
    setLoginStatus('');
    setLoginCurrent(1);
    setLoginFilter({ username: '' });
  };

  const operationRecords = operationsPage?.records || [];
  const loginRecords = loginsPage?.records || [];
  const activePage = tab === 'operation' ? operationsPage : loginsPage;
  const currentPage = tab === 'operation' ? operationCurrent : loginCurrent;
  const total = activePage?.total || 0;
  const totalPages = Math.max(activePage?.pages || (activePage ? Math.ceil(total / (activePage.size || PAGE_SIZE)) : 1) || 1, 1);

  const goToPreviousPage = () => {
    if (tab === 'operation') setOperationCurrent((current) => Math.max(1, current - 1));
    else setLoginCurrent((current) => Math.max(1, current - 1));
  };

  const goToNextPage = () => {
    if (tab === 'operation') setOperationCurrent((current) => Math.min(totalPages, current + 1));
    else setLoginCurrent((current) => Math.min(totalPages, current + 1));
  };

  const hasRecords = tab === 'operation' ? operationRecords.length > 0 : loginRecords.length > 0;

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
        {canViewOperation && <button type="button" onClick={() => setTab('operation')} className={`pb-2 text-xs font-semibold border-b-2 ${tab === 'operation' ? 'text-indigo-600 border-indigo-600' : 'text-gray-500 border-transparent'}`}>操作日志</button>}
        {canViewLogin && <button type="button" onClick={() => setTab('login')} className={`pb-2 text-xs font-semibold border-b-2 ${tab === 'login' ? 'text-indigo-600 border-indigo-600' : 'text-gray-500 border-transparent'}`}>登录日志</button>}
      </div>

      {tab === 'operation' ? (
        <form className="flex flex-wrap items-end gap-2 mb-4" onSubmit={submitOperationFilter}>
          <label className="flex flex-col gap-1 text-xs text-gray-500">用户名<input value={operationUsername} onChange={(event) => setOperationUsername(event.target.value)} placeholder="按用户名搜索" aria-label="操作日志用户名" className="h-8 w-40 px-2.5 rounded-lg border border-gray-200 text-xs text-gray-700 outline-none focus:border-indigo-400" /></label>
          <label className="flex flex-col gap-1 text-xs text-gray-500">模块标识<input value={operationModuleKey} onChange={(event) => setOperationModuleKey(event.target.value)} placeholder="如 product" aria-label="操作日志模块标识" className="h-8 w-40 px-2.5 rounded-lg border border-gray-200 text-xs text-gray-700 outline-none focus:border-indigo-400" /></label>
          <button type="submit" className="h-8 px-3 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700">查询</button>
          <button type="button" onClick={resetOperationFilter} className="h-8 px-3 rounded-lg border border-gray-200 text-xs font-semibold text-gray-600 hover:bg-gray-50">重置</button>
        </form>
      ) : (
        <form className="flex flex-wrap items-end gap-2 mb-4" onSubmit={submitLoginFilter}>
          <label className="flex flex-col gap-1 text-xs text-gray-500">用户名<input value={loginUsername} onChange={(event) => setLoginUsername(event.target.value)} placeholder="按用户名搜索" aria-label="登录日志用户名" className="h-8 w-40 px-2.5 rounded-lg border border-gray-200 text-xs text-gray-700 outline-none focus:border-indigo-400" /></label>
          <label className="flex flex-col gap-1 text-xs text-gray-500">登录状态<select value={loginStatus} onChange={(event) => setLoginStatus(event.target.value as LoginStatusFilter)} aria-label="登录日志状态" className="h-8 w-32 px-2.5 rounded-lg border border-gray-200 text-xs text-gray-700 bg-white outline-none focus:border-indigo-400"><option value="">全部状态</option><option value="1">成功</option><option value="0">失败</option></select></label>
          <button type="submit" className="h-8 px-3 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700">查询</button>
          <button type="button" onClick={resetLoginFilter} className="h-8 px-3 rounded-lg border border-gray-200 text-xs font-semibold text-gray-600 hover:bg-gray-50">重置</button>
        </form>
      )}

      <div className="overflow-x-auto">
        {error ? (
          <div className="py-8 text-center text-xs text-red-600" role="alert"><p>{error}</p><button type="button" onClick={() => void loadLogs()} disabled={loading} className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800"><RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />重试</button></div>
        ) : loading ? (
          <p className="py-8 text-center text-xs text-gray-400" role="status">日志加载中...</p>
        ) : !hasRecords ? (
          <p className="py-8 text-center text-xs text-gray-400">暂无日志记录，请调整筛选条件后重试</p>
        ) : tab === 'operation' ? (
          <table className="w-full text-xs"><thead><tr className="text-left text-gray-500 border-b"><th className="py-2 pr-3">时间</th><th className="py-2 pr-3">用户</th><th className="py-2 pr-3">模块/操作</th><th className="py-2 pr-3">请求</th><th className="py-2">结果</th></tr></thead><tbody>{operationRecords.map((item) => <tr key={item.id} className="border-b border-gray-50"><td className="py-2 pr-3 whitespace-nowrap">{item.createdAt || '-'}</td><td className="py-2 pr-3">{item.username || '-'}</td><td className="py-2 pr-3">{item.moduleKey || '-'} / {item.operation || '-'}</td><td className="py-2 pr-3 font-mono">{item.requestMethod || '-'} {item.requestUri || '-'}</td><td className={`py-2 font-semibold ${item.responseStatus && item.responseStatus >= 400 ? 'text-red-600' : 'text-emerald-600'}`}>{item.responseStatus || '-'}</td></tr>)}</tbody></table>
        ) : (
          <table className="w-full text-xs"><thead><tr className="text-left text-gray-500 border-b"><th className="py-2 pr-3">时间</th><th className="py-2 pr-3">用户</th><th className="py-2 pr-3">状态</th><th className="py-2 pr-3">IP</th><th className="py-2">失败原因</th></tr></thead><tbody>{loginRecords.map((item) => <tr key={item.id} className="border-b border-gray-50"><td className="py-2 pr-3 whitespace-nowrap">{item.loginAt || '-'}</td><td className="py-2 pr-3">{item.username || '-'}</td><td className={`py-2 pr-3 font-semibold ${item.loginStatus === 1 ? 'text-emerald-600' : 'text-red-600'}`}>{item.loginStatus === 1 ? '成功' : '失败'}</td><td className="py-2 pr-3">{item.loginIp || '-'}</td><td className="py-2">{item.failureReason || '-'}</td></tr>)}</tbody></table>
        )}
      </div>

      {!error && !loading && activePage && (
        <div className="flex flex-wrap items-center justify-between gap-2 mt-4 pt-3 border-t border-gray-100 text-xs text-gray-500">
          <span>共 {total} 条，第 {Math.min(currentPage, totalPages)} / {totalPages} 页</span>
          <div className="flex items-center gap-2"><button type="button" onClick={goToPreviousPage} disabled={loading || currentPage <= 1} className="h-8 px-3 rounded-lg border border-gray-200 font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed">上一页</button><button type="button" onClick={goToNextPage} disabled={loading || currentPage >= totalPages} className="h-8 px-3 rounded-lg border border-gray-200 font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed">下一页</button></div>
        </div>
      )}
    </section>
  );
};
