import React, { useEffect, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Clock, Download, Loader2, RefreshCw, RotateCcw, Trash2 } from 'lucide-react';
import { BackendExportTask, BackendExportTaskStatus } from '../../api/adminApi';
import { useExportCenter } from '../../context/ExportCenterContext';

/** 状态展示文案与配色，集中在这里保证列表和角标口径一致。 */
const STATUS_META: Record<BackendExportTaskStatus, { label: string; className: string }> = {
  PENDING: { label: '排队中', className: 'bg-amber-50 text-amber-700 border-amber-200' },
  RUNNING: { label: '生成中', className: 'bg-blue-50 text-blue-700 border-blue-200' },
  SUCCESS: { label: '已完成', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  FAILED: { label: '生成失败', className: 'bg-red-50 text-red-700 border-red-200' },
  EXPIRED: { label: '已过期', className: 'bg-gray-100 text-gray-600 border-gray-200' },
};

function formatFileSize(size: number | null): string {
  if (!size || size <= 0) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
}

/** 兼容后端返回的 ISO 日期时间与「空格分隔」两种写法。 */
function formatTime(value: string | null): string {
  if (!value) return '';
  const date = new Date(value.includes('T') ? value : value.replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return value;
  const pad = (input: number) => String(input).padStart(2, '0');
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const StatusIcon: React.FC<{ status: BackendExportTaskStatus }> = ({ status }) => {
  if (status === 'SUCCESS') return <CheckCircle2 className="w-4 h-4 text-emerald-600" />;
  if (status === 'FAILED') return <AlertCircle className="w-4 h-4 text-red-500" />;
  if (status === 'EXPIRED') return <Clock className="w-4 h-4 text-gray-400" />;
  return <Loader2 className="w-4 h-4 text-blue-600 animate-spin" />;
};

const ExportTaskRow: React.FC<{
  task: BackendExportTask;
  canDownload: boolean;
  onDownload: (task: BackendExportTask) => void;
  onRetry: (task: BackendExportTask) => void;
  onRemove: (task: BackendExportTask) => void;
}> = ({ task, canDownload, onDownload, onRetry, onRemove }) => {
  const meta = STATUS_META[task.status] || STATUS_META.PENDING;
  const busy = task.status === 'PENDING' || task.status === 'RUNNING';
  const actionClass = 'p-1.5 rounded-md text-gray-500 hover:text-blue-700 hover:bg-blue-50 transition-colors cursor-pointer';

  return (
    <div className="flex items-start gap-3 px-3.5 py-3 hover:bg-[#F8FAFC]">
      <div className="mt-0.5 shrink-0">
        <StatusIcon status={task.status} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-gray-900 truncate" title={task.exportName}>{task.exportName}</span>
          <span className={`shrink-0 text-[10px] font-bold px-1.5 py-0.5 rounded border ${meta.className}`}>{meta.label}</span>
        </div>
        <p className="text-[11px] text-gray-500 mt-1 truncate font-mono">
          {task.taskNo || `#${task.id}`} · {formatTime(task.createdAt)}
        </p>
        {task.status === 'SUCCESS' && (
          <p className="text-[11px] text-gray-500 mt-0.5">
            {Number(task.rowCount || 0).toLocaleString('zh-CN')} 行 · {formatFileSize(task.fileSize)} · 保留至 {formatTime(task.expiresAt)}
          </p>
        )}
        {task.status === 'FAILED' && task.errorMessage && (
          <p className="text-[11px] text-red-600 mt-0.5 break-words" title={task.errorMessage}>{task.errorMessage}</p>
        )}
        {task.status === 'EXPIRED' && (
          <p className="text-[11px] text-gray-400 mt-0.5">文件已按保留策略清理，可重新发起导出</p>
        )}
      </div>
      <div className="flex items-center gap-1 shrink-0">
        {task.downloadable && canDownload && (
          <button type="button" className={actionClass} title="下载文件" aria-label="下载文件" onClick={() => onDownload(task)}>
            <Download className="w-4 h-4" />
          </button>
        )}
        {task.status === 'FAILED' && (
          <button type="button" className={actionClass} title="重新生成" aria-label="重新生成" onClick={() => onRetry(task)}>
            <RotateCcw className="w-4 h-4" />
          </button>
        )}
        {!busy && (
          <button type="button" className={`${actionClass} hover:text-red-600 hover:bg-red-50`} title="移除记录" aria-label="移除记录" onClick={() => onRemove(task)}>
            <Trash2 className="w-4 h-4" />
          </button>
        )}
      </div>
    </div>
  );
};

/**
 * 顶栏下载中心。
 *
 * <p>导出任务改为后台异步生成后，提交动作与取件动作分离，这里汇总当前管理员的任务状态：
 * 有任务在排队或生成中时角标实时提醒，任务完成后可直接取件，失败可就地重试。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
export const ExportCenter: React.FC = () => {
  const { tasks, loading, activeCount, canUseExportCenter, canDownloadExport, refresh, download, retry, remove } = useExportCenter();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return undefined;
    const handlePointerDown = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, [open]);

  if (!canUseExportCenter) return null;

  return (
    <div className="relative" ref={containerRef}>
      <button
        id="btn-export-center"
        type="button"
        onClick={() => setOpen((previous) => !previous)}
        aria-label="下载中心"
        aria-expanded={open}
        aria-haspopup="menu"
        className="relative text-gray-600 hover:text-gray-900 hover:bg-gray-200/70 p-2 rounded-full transition-colors cursor-pointer"
      >
        <Download className="w-5 h-5" />
        {activeCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-blue-600 text-white text-[10px] font-bold flex items-center justify-center ring-2 ring-[#F8FAFC]">
            {activeCount}
          </span>
        )}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute right-0 mt-2 w-[360px] sm:w-[400px] bg-white rounded-xl shadow-lg border border-[#E2E8F0] z-50 overflow-hidden animate-in fade-in-50 zoom-in-95">
            <div className="flex items-center justify-between px-3.5 py-2.5 border-b border-[#E2E8F0]">
              <div className="flex items-center gap-2">
                <Download className="w-4 h-4 text-blue-600" />
                <span className="text-sm font-semibold text-gray-900">下载中心</span>
                {activeCount > 0 && (
                  <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-blue-50 text-blue-700 border border-blue-200">
                    {activeCount} 个进行中
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={() => void refresh()}
                className="p-1.5 rounded-md text-gray-500 hover:text-blue-700 hover:bg-blue-50 transition-colors cursor-pointer"
                title="刷新列表"
                aria-label="刷新导出任务列表"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              </button>
            </div>

            <div className="max-h-[360px] overflow-y-auto divide-y divide-gray-100">
              {loading && tasks.length === 0 ? (
                <div className="py-10 text-center text-xs text-gray-400" role="status">正在加载导出任务…</div>
              ) : tasks.length === 0 ? (
                <div className="py-10 px-6 text-center">
                  <Download className="w-6 h-6 mx-auto text-gray-300 mb-2" />
                  <p className="text-xs text-gray-500">暂无导出任务</p>
                  <p className="text-[11px] text-gray-400 mt-1">在任意业务页面点击导出，任务会在这里异步生成</p>
                </div>
              ) : (
                tasks.map((task) => (
                  <ExportTaskRow
                    key={task.id}
                    task={task}
                    canDownload={canDownloadExport}
                    onDownload={(item) => void download(item)}
                    onRetry={(item) => void retry(item)}
                    onRemove={(item) => void remove(item)}
                  />
                ))
              )}
            </div>

            <div className="px-3.5 py-2 border-t border-[#E2E8F0] bg-[#F8FAFC]/60 text-[11px] text-gray-500">
              导出文件保存在对象存储中，按保留策略自动清理，请及时下载。
            </div>
          </div>
        </>
      )}
    </div>
  );
};
