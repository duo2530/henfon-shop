import React, { createContext, useCallback, useContext, useEffect, useRef, useState, ReactNode } from 'react';
import {
  BackendExportTask,
  ExportTaskRequest,
  deleteExportTask,
  downloadExportTaskFile,
  listExportTasks,
  retryExportTask,
  submitExportTask,
} from '../api/adminApi';
import { useAdmin } from './AdminContext';

/** 下载中心自身的查询权限，没有该权限时不轮询也不展示入口。 */
const EXPORT_CENTER_PERMISSION = 'export:task:query';

/** 取件权限。只有查询权限的账号能看任务列表，但不能下载文件，后端也会拒绝。 */
const EXPORT_DOWNLOAD_PERMISSION = 'export:task:download';

/** 存在排队/生成中的任务时用较短的轮询间隔，任务全部结束后放宽，避免无谓请求。 */
const ACTIVE_POLL_INTERVAL_MS = 3_000;

const IDLE_POLL_INTERVAL_MS = 10_000;

const PAGE_SIZE = 20;

interface ExportCenterContextType {
  tasks: BackendExportTask[];
  loading: boolean;
  submitting: boolean;
  /** 排队中或生成中的任务数量，用于顶栏角标。 */
  activeCount: number;
  canUseExportCenter: boolean;
  /** 是否具备取件权限；为 false 时下载按钮不渲染，避免点击后被后端拒绝。 */
  canDownloadExport: boolean;
  refresh: () => Promise<void>;
  /** 提交导出任务，成功返回 true；失败已提示，调用方只需处理按钮状态。 */
  submit: (request: ExportTaskRequest) => Promise<boolean>;
  download: (task: BackendExportTask) => Promise<void>;
  retry: (task: BackendExportTask) => Promise<void>;
  remove: (task: BackendExportTask) => Promise<void>;
}

const ExportCenterContext = createContext<ExportCenterContextType | undefined>(undefined);

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

export const ExportCenterProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { currentUser, isAuthenticated, hasPermission, showToast, confirm } = useAdmin();
  const [tasks, setTasks] = useState<BackendExportTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  // 轮询请求可能重叠，用引用记录进行中的加载，避免列表来回抖动。
  const inFlight = useRef(false);
  const hasTasks = useRef(false);

  const canUseExportCenter = isAuthenticated && hasPermission(EXPORT_CENTER_PERMISSION);

  const canDownloadExport = isAuthenticated && hasPermission(EXPORT_DOWNLOAD_PERMISSION);

  useEffect(() => {
    hasTasks.current = tasks.length > 0;
  }, [tasks]);

  const refresh = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    // 首次加载显示占位，后台轮询静默更新，避免列表反复闪动。
    if (!hasTasks.current) setLoading(true);
    try {
      const page = await listExportTasks({ current: 1, size: PAGE_SIZE });
      setTasks(Array.isArray(page?.records) ? page.records : []);
    } catch (error) {
      // 轮询失败不打断页面，仅在控制台留痕，下一次轮询会自动重试。
      console.warn('导出任务列表刷新失败', error);
    } finally {
      inFlight.current = false;
      setLoading(false);
    }
  }, []);

  const submit = useCallback(async (request: ExportTaskRequest): Promise<boolean> => {
    setSubmitting(true);
    try {
      const task = await submitExportTask(request);
      setTasks((previous) => [task, ...previous.filter((item) => item.id !== task.id)]);
      showToast(`已提交「${task.exportName}」，生成完成后可在顶部下载中心取件`, 'success');
      return true;
    } catch (error) {
      showToast(errorMessage(error, '导出任务提交失败，请稍后重试'), 'error');
      return false;
    } finally {
      setSubmitting(false);
    }
  }, [showToast]);

  const download = useCallback(async (task: BackendExportTask) => {
    try {
      const blob = await downloadExportTaskFile(task.id);
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = task.fileName || `${task.exportName}.xlsx`;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(objectUrl);
      showToast(`已开始下载「${anchor.download}」`, 'success');
    } catch (error) {
      showToast(errorMessage(error, '文件下载失败，请稍后重试'), 'error');
      // 文件可能已被清理，重新拉取列表同步最新状态。
      await refresh();
    }
  }, [refresh, showToast]);

  const retry = useCallback(async (task: BackendExportTask) => {
    try {
      const requeued = await retryExportTask(task.id);
      setTasks((previous) => previous.map((item) => item.id === task.id ? { ...item, ...requeued } : item));
      showToast(`已重新提交「${task.exportName}」`, 'info');
    } catch (error) {
      showToast(errorMessage(error, '任务重试失败，请稍后重试'), 'error');
      await refresh();
    }
  }, [refresh, showToast]);

  const remove = useCallback(async (task: BackendExportTask) => {
    const approved = await confirm(`确认从下载中心移除「${task.exportName}」？已生成的文件也会一并清理。`, '移除导出记录');
    if (!approved) return;
    try {
      await deleteExportTask(task.id);
      setTasks((previous) => previous.filter((item) => item.id !== task.id));
      showToast('导出记录已移除', 'info');
    } catch (error) {
      showToast(errorMessage(error, '移除失败，请稍后重试'), 'error');
      await refresh();
    }
  }, [confirm, refresh, showToast]);

  const activeCount = tasks.filter((task) => task.status === 'PENDING' || task.status === 'RUNNING').length;

  useEffect(() => {
    if (!canUseExportCenter) {
      setTasks([]);
      return undefined;
    }
    void refresh();
    const interval = window.setInterval(() => {
      // 页面处于后台时不轮询，回到前台会由 visibilitychange 立即补一次。
      if (document.hidden) return;
      void refresh();
    }, activeCount > 0 ? ACTIVE_POLL_INTERVAL_MS : IDLE_POLL_INTERVAL_MS);
    return () => window.clearInterval(interval);
  }, [canUseExportCenter, currentUser?.userId, activeCount, refresh]);

  useEffect(() => {
    if (!canUseExportCenter) return undefined;
    const handleVisible = () => {
      if (!document.hidden) void refresh();
    };
    window.addEventListener('visibilitychange', handleVisible);
    return () => window.removeEventListener('visibilitychange', handleVisible);
  }, [canUseExportCenter, refresh]);

  return (
    <ExportCenterContext.Provider
      value={{ tasks, loading, submitting, activeCount, canUseExportCenter, canDownloadExport, refresh, submit, download, retry, remove }}
    >
      {children}
    </ExportCenterContext.Provider>
  );
};

export const useExportCenter = (): ExportCenterContextType => {
  const context = useContext(ExportCenterContext);
  if (!context) {
    throw new Error('useExportCenter must be used within an ExportCenterProvider');
  }
  return context;
};
