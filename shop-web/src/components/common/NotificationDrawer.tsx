import React, { useCallback, useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import {
  getContentNotificationSummary,
  listContentNotifications,
  type BackendContentNotification,
  type BackendContentNotificationSummary
} from '../../api/adminApi';
import { formatMinute } from '../../utils/datetime';
import type { NavigationTab } from '../../types';
import {
  AlertTriangle,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  Clock,
  CreditCard,
  Info,
  Loader2,
  PackageCheck,
  RotateCcw,
  Search,
  Star,
  Truck,
  X,
  type LucideIcon
} from 'lucide-react';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';

interface NotificationDrawerProps {
  open: boolean;
  onClose: () => void;
}

const PAGE_SIZE = 20;

interface EventMeta {
  /** 中文事件名，列表上作为类型标签。 */
  label: string;
  /** 图标容器的配色。 */
  tone: string;
  Icon: LucideIcon;
  /** 点击后跳转的管理端页面，不配置则只标记已读。 */
  tab?: NavigationTab;
}

/**
 * 事件类型展示配置。
 *
 * 事件类型由后端各业务监听器写入，这里只负责展示与跳转，
 * 未列出的类型统一走兜底样式，不会因为后端新增事件而渲染异常。
 */
const EVENT_META: Record<string, EventMeta> = {
  PAYMENT_SUCCEEDED: { label: '支付成功', tone: 'bg-emerald-50 text-emerald-600', Icon: CreditCard, tab: 'orders' },
  ORDER_SHIPPED: { label: '订单发货', tone: 'bg-blue-50 text-blue-600', Icon: Truck, tab: 'orders' },
  REFUND_SUCCEEDED: { label: '退款成功', tone: 'bg-amber-50 text-amber-600', Icon: RotateCcw, tab: 'orders' },
  AFTER_SALE_CREATED: { label: '售后申请', tone: 'bg-amber-50 text-amber-600', Icon: AlertTriangle, tab: 'orders' },
  AFTER_SALE_APPROVED: { label: '售后通过', tone: 'bg-emerald-50 text-emerald-600', Icon: CheckCheck, tab: 'orders' },
  AFTER_SALE_REJECTED: { label: '售后驳回', tone: 'bg-rose-50 text-rose-600', Icon: AlertTriangle, tab: 'orders' },
  AFTER_SALE_RETURN_RECEIVED: { label: '退货已收', tone: 'bg-blue-50 text-blue-600', Icon: PackageCheck, tab: 'orders' },
  AFTER_SALE_COMPLETED: { label: '售后完成', tone: 'bg-emerald-50 text-emerald-600', Icon: CheckCheck, tab: 'orders' },
  AFTER_SALE_CANCELLED: { label: '售后取消', tone: 'bg-gray-100 text-gray-500', Icon: AlertTriangle, tab: 'orders' },
  REVIEW_APPROVED: { label: '评价通过', tone: 'bg-emerald-50 text-emerald-600', Icon: Star, tab: 'content_reviews' },
  REVIEW_HIDDEN: { label: '评价未通过', tone: 'bg-gray-100 text-gray-500', Icon: Star, tab: 'content_reviews' }
};

const FALLBACK_META: EventMeta = { label: '系统通知', tone: 'bg-blue-50 text-blue-600', Icon: Info };

function eventMeta(eventType: string): EventMeta {
  return EVENT_META[eventType] ?? FALLBACK_META;
}

export const NotificationDrawer: React.FC<NotificationDrawerProps> = ({ open, onClose }) => {
  const { notificationUnreadCount, markNotificationAsRead, markAllNotificationsAsRead, setCurrentTab, showToast } = useAdmin();
  const [records, setRecords] = useState<BackendContentNotification[]>([]);
  const [summary, setSummary] = useState<BackendContentNotificationSummary | null>(null);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(0);
  const [current, setCurrent] = useState(1);
  const [eventType, setEventType] = useState('');
  const [adminReadStatus, setAdminReadStatus] = useState<'' | '0' | '1'>('');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [marking, setMarking] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listContentNotifications({
        current,
        size: PAGE_SIZE,
        eventType: eventType || undefined,
        adminReadStatus: adminReadStatus === '' ? undefined : (Number(adminReadStatus) as 0 | 1),
        keyword: keyword || undefined
      });
      setRecords(page.records ?? []);
      setTotal(Number(page.total ?? 0));
      setPages(Number(page.pages ?? 0));
    } catch (error) {
      setRecords([]);
      setTotal(0);
      setPages(0);
      showToast(error instanceof Error ? error.message : '通知加载失败', 'error');
    } finally {
      setLoading(false);
    }
  }, [current, eventType, adminReadStatus, keyword, showToast]);

  const loadSummary = useCallback(async () => {
    try {
      setSummary(await getContentNotificationSummary());
    } catch {
      // 概览失败不影响列表本身，只是筛选项与标题数字降级。
      setSummary(null);
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    void loadSummary();
  }, [open, loadSummary]);

  useEffect(() => {
    if (!open) return;
    void load();
  }, [open, load]);

  // 弹层打开期间锁住底层文档滚动，避免出现滚动穿透。
  useBodyScrollLock(Boolean(open));
  if (!open) return null;

  /** 切换筛选条件时回到第一页，避免停留在越界页看到空列表。 */
  const changeFilter = (apply: () => void) => {
    setCurrent(1);
    apply();
  };

  const handleItemClick = async (item: BackendContentNotification) => {
    if (item.adminReadStatus === 0) {
      await markNotificationAsRead(item.id);
      setRecords((prev) => prev.map((record) => (record.id === item.id ? { ...record, adminReadStatus: 1 as const } : record)));
      setSummary((prev) => (prev ? { ...prev, adminUnread: Math.max(prev.adminUnread - 1, 0) } : prev));
    }
    const tab = eventMeta(item.eventType).tab;
    if (tab) {
      setCurrentTab(tab);
      onClose();
    }
  };

  const handleMarkAll = async () => {
    if (notificationUnreadCount === 0) {
      showToast('当前没有未读通知', 'info');
      return;
    }
    setMarking(true);
    try {
      await markAllNotificationsAsRead();
      setRecords((prev) => prev.map((record) => ({ ...record, adminReadStatus: 1 as const })));
      setSummary((prev) => (prev ? { ...prev, adminUnread: 0 } : prev));
    } finally {
      setMarking(false);
    }
  };

  const totalPages = pages > 0 ? pages : (total > 0 ? 1 : 0);
  const eventTypeOptions = summary?.eventTypes ?? [];

  return (
    <>
      <div
        className="fixed inset-0 bg-black/40 z-50 backdrop-blur-2xs transition-opacity"
        onClick={onClose}
      />
      <div className="fixed top-0 right-0 h-full w-full max-w-lg bg-white shadow-2xl z-50 flex flex-col border-l border-[#E2E8F0] animate-in slide-in-from-right duration-200">
        {/* Header */}
        <div className="p-4 border-b border-gray-200 bg-gray-50/70">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="font-semibold text-gray-900 text-base">系统通知</h3>
              <p className="text-xs text-gray-500 mt-0.5">
                平台发给会员的站内通知投递记录，共 {summary?.total ?? 0} 条，运营未读 {notificationUnreadCount} 条
              </p>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={handleMarkAll}
                disabled={marking || notificationUnreadCount === 0}
                className="text-xs text-blue-600 hover:text-blue-800 font-medium flex items-center gap-1 p-1.5 hover:bg-blue-50 rounded transition-colors disabled:text-gray-300 disabled:hover:bg-transparent disabled:cursor-not-allowed"
                title="全部标为已读"
              >
                {marking ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCheck className="w-4 h-4" />}
                <span>全部已读</span>
              </button>
              <button
                onClick={onClose}
                className="text-gray-400 hover:text-gray-700 p-1.5 rounded-lg hover:bg-gray-200 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
          </div>

          {/* Filters */}
          <div className="mt-3 flex items-center gap-2">
            <select
              value={eventType}
              onChange={(event) => changeFilter(() => setEventType(event.target.value))}
              className="flex-1 min-w-0 text-xs border border-gray-200 rounded-lg px-2 py-1.5 bg-white text-gray-700 focus:outline-hidden focus:border-blue-400"
            >
              <option value="">全部类型</option>
              {eventTypeOptions.map((option) => (
                <option key={option.eventType} value={option.eventType}>
                  {eventMeta(option.eventType).label}（{option.count}）
                </option>
              ))}
            </select>
            <select
              value={adminReadStatus}
              onChange={(event) => changeFilter(() => setAdminReadStatus(event.target.value as '' | '0' | '1'))}
              className="flex-1 min-w-0 text-xs border border-gray-200 rounded-lg px-2 py-1.5 bg-white text-gray-700 focus:outline-hidden focus:border-blue-400"
            >
              <option value="">全部状态</option>
              <option value="0">运营未读</option>
              <option value="1">运营已读</option>
            </select>
            <button
              onClick={() => { void loadSummary(); void load(); }}
              className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
              title="刷新"
            >
              <Clock className="w-4 h-4" />
            </button>
          </div>

          <div className="mt-2 relative">
            <Search className="w-3.5 h-3.5 text-gray-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
            <input
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') changeFilter(() => setKeyword(keywordInput.trim()));
              }}
              placeholder="搜索标题、内容或业务单号，回车确认"
              className="w-full text-xs border border-gray-200 rounded-lg pl-8 pr-16 py-1.5 bg-white text-gray-700 focus:outline-hidden focus:border-blue-400"
            />
            <button
              onClick={() => changeFilter(() => setKeyword(keywordInput.trim()))}
              className="absolute right-1.5 top-1/2 -translate-y-1/2 text-xs text-blue-600 hover:text-blue-800 px-2 py-0.5 rounded hover:bg-blue-50"
            >
              搜索
            </button>
          </div>
        </div>

        {/* Notifications List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {loading ? (
            <div className="text-center py-16 text-gray-400">
              <Loader2 className="w-6 h-6 mx-auto mb-2 animate-spin" />
              <p className="text-sm">正在加载通知…</p>
            </div>
          ) : records.length === 0 ? (
            <div className="text-center py-16 text-gray-400">
              <Info className="w-10 h-10 mx-auto mb-2 opacity-40" />
              <p className="text-sm">没有符合条件的通知</p>
            </div>
          ) : (
            records.map((item) => {
              const meta = eventMeta(item.eventType);
              const unread = item.adminReadStatus === 0;
              return (
                <div
                  key={item.id}
                  onClick={() => { void handleItemClick(item); }}
                  className={`p-3.5 rounded-xl border transition-all cursor-pointer flex gap-3 ${
                    unread
                      ? 'bg-blue-50/40 border-blue-200 shadow-2xs hover:border-blue-300'
                      : 'bg-white border-gray-200 opacity-75'
                  }`}
                >
                  <div className={`w-9 h-9 rounded-lg ${meta.tone} flex items-center justify-center flex-shrink-0 mt-0.5`}>
                    <meta.Icon className="w-5 h-5" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2 mb-1">
                      <h4 className="text-sm font-semibold text-gray-900 truncate">
                        {item.title}
                      </h4>
                      {unread && (
                        <span className="w-2 h-2 rounded-full bg-blue-600 flex-shrink-0" />
                      )}
                    </div>
                    <p className="text-xs text-gray-600 leading-relaxed line-clamp-2">
                      {item.content}
                    </p>
                    <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-gray-400">
                      <span className={`px-1.5 py-0.5 rounded ${meta.tone}`}>{meta.label}</span>
                      <span className="text-gray-500">发给 {item.memberName}</span>
                      {item.memberAccount && <span className="truncate max-w-[160px]">{item.memberAccount}</span>}
                      <span className={item.readStatus === 1 ? 'text-emerald-600' : 'text-amber-600'}>
                        会员{item.readStatus === 1 ? '已读' : '未读'}
                      </span>
                      <span className="flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        {formatMinute(item.createdAt)}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Pagination */}
        <div className="p-3 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500 bg-gray-50/70">
          <span>共 {total} 条</span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setCurrent((prev) => Math.max(prev - 1, 1))}
              disabled={current <= 1 || loading}
              className="p-1 rounded-lg hover:bg-gray-200 disabled:text-gray-300 disabled:hover:bg-transparent disabled:cursor-not-allowed"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span>
              第 {current} / {totalPages || 1} 页
            </span>
            <button
              onClick={() => setCurrent((prev) => Math.min(prev + 1, Math.max(totalPages, 1)))}
              disabled={current >= totalPages || loading}
              className="p-1 rounded-lg hover:bg-gray-200 disabled:text-gray-300 disabled:hover:bg-transparent disabled:cursor-not-allowed"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </>
  );
};
