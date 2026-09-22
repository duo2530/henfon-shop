import React, { useCallback, useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { BackendAiTicket, getAiTicket, handleAiTicket, listAiTickets } from '../../api/adminApi';
import { PermissionGate } from '../common/PermissionGate';
import { formatDateTime } from '../../utils/datetime';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle,
  Headphones,
  Loader2,
  RefreshCw,
  Search,
  Send,
} from 'lucide-react';

const PAGE_SIZE = 20;

type TicketStatus = BackendAiTicket['status'];

/** 状态展示口径。待处理用琥珀色是最需要被注意的一档，已关闭刻意压暗，避免它抢视线。 */
const STATUS_META: Record<TicketStatus, { label: string; chip: string }> = {
  PENDING: { label: '待处理', chip: 'bg-amber-50 text-amber-700 border-amber-200' },
  PROCESSING: { label: '处理中', chip: 'bg-blue-50 text-blue-700 border-blue-200' },
  CLOSED: { label: '已关闭', chip: 'bg-slate-100 text-slate-600 border-slate-200' },
};

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'CLOSED', label: '已关闭' },
];

function statusMeta(status?: string) {
  return STATUS_META[status as TicketStatus] || { label: status || '-', chip: 'bg-slate-100 text-slate-600 border-slate-200' };
}

/**
 * AI 客服转人工工单管理页。
 *
 * 买家在客服窗口点「转人工」后落成的工单都在这里，问题原文不做改写，运营看到的就是买家
 * 打出来的那句话。列表与详情在同一个组件里切换而不是各占一个导航项：工单是"看一眼就走"
 * 的场景，跳走再回来还要重新筛一遍会很难用，所以返回列表时筛选条件与页码都留在原地。
 *
 * @author Henfon
 * @date 2026-09-21
 */
export const AiTicketManagementView: React.FC = () => {
  const { showToast } = useAdmin();

  const [view, setView] = useState<'list' | 'detail'>('list');
  const [records, setRecords] = useState<BackendAiTicket[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 输入框里的值与真正生效的筛选条件分开：只有点查询或翻页时才把输入提交到筛选。
  const [statusInput, setStatusInput] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [keywordFilter, setKeywordFilter] = useState('');

  const [detail, setDetail] = useState<BackendAiTicket | null>(null);
  const [handleStatus, setHandleStatus] = useState<TicketStatus>('PROCESSING');
  const [handleNote, setHandleNote] = useState('');
  const [handleReply, setHandleReply] = useState('');
  const [saving, setSaving] = useState(false);

  const loadTickets = useCallback(async (page: number) => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await listAiTickets({
        current: page,
        size: PAGE_SIZE,
        status: statusFilter || undefined,
        keyword: keywordFilter.trim() || undefined,
      });
      setRecords(data.records || []);
      setTotal(data.total || 0);
      setTotalPages(Math.max(data.pages || 1, 1));
      setCurrent(data.current || page);
    } catch (error) {
      const message = error instanceof Error ? error.message : '工单加载失败';
      setRecords([]);
      setTotal(0);
      setTotalPages(1);
      setLoadError(message);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, keywordFilter]);

  useEffect(() => {
    void loadTickets(current);
    // 筛选条件变化时回到第一页由查询按钮显式触发，这里只在筛选或页码变化时重新拉取。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadTickets, current]);

  const openDetail = (ticket: BackendAiTicket) => {
    setDetail(ticket);
    setHandleStatus(ticket.status || 'PROCESSING');
    setHandleNote(ticket.handleNote || '');
    setHandleReply(ticket.replyContent || '');
    setView('detail');
    // 列表数据刚刚拉过，先用它把详情渲染出来，再取一次保证看到的是最新的处理状态
    // （同一个工单可能刚被另一位运营处理）。取失败就保留列表里的那份数据。
    void getAiTicket(ticket.id)
      .then((fresh) => setDetail(fresh))
      .catch(() => undefined);
  };

  const backToList = () => {
    setView('list');
    setDetail(null);
  };

  const submitHandle = async () => {
    if (!detail) return;
    const ticketId = detail.id;
    setSaving(true);
    try {
      const updated = await handleAiTicket(ticketId, {
        status: handleStatus,
        handleNote: handleNote.trim() || undefined,
        // 留空表示本次不更新回复（服务端同样按"不覆盖"处理），避免改一个状态就把已发出的
        // 答复抹掉。要覆盖就填新内容。
        reply: handleReply.trim() || undefined,
      });
      // 就地更新当前行，避免为了看一个新状态而整页重拉。
      setRecords((prev) => prev.map((item) => (item.id === ticketId ? updated : item)));
      setDetail(updated);
      setHandleNote(updated.handleNote || '');
      setHandleReply(updated.replyContent || '');
      showToast(`工单 ${updated.ticketNo || ticketId} 已更新为「${statusMeta(updated.status).label}」`, 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '工单处理失败，请稍后重试', 'error');
    } finally {
      setSaving(false);
    }
  };

  if (view === 'detail' && detail) {
    const meta = statusMeta(detail.status);
    return (
      <div className="space-y-6 animate-in fade-in-50 duration-200">
        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={backToList}
            className="h-8 px-3 rounded-lg border border-[#E2E8F0] text-xs font-semibold text-gray-600 hover:bg-white inline-flex items-center gap-1.5"
          >
            <ArrowLeft className="w-3.5 h-3.5" />返回列表
          </button>
          <h2 className="text-lg font-bold text-[#191C1E] tracking-tight">
            {detail.ticketNo || `工单 #${detail.id}`}
          </h2>
          <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${meta.chip}`}>{meta.label}</span>
        </div>

        <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 md:p-6 space-y-4">
          <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 text-xs">
            <div>
              <dt className="text-gray-500 mb-1">联系方式</dt>
              <dd className="text-gray-900 font-medium break-all">{detail.contact || '-'}</dd>
            </div>
            <div>
              <dt className="text-gray-500 mb-1">提交时间</dt>
              <dd className="text-gray-900 font-medium">{formatDateTime(detail.createdAt, '-')}</dd>
            </div>
            <div>
              <dt className="text-gray-500 mb-1">来源会话</dt>
              <dd className="text-gray-900 font-medium break-all">{detail.conversationId || '直接留言'}</dd>
            </div>
            <div>
              <dt className="text-gray-500 mb-1">处理人</dt>
              <dd className="text-gray-900 font-medium">
                {detail.handlerName || '未处理'}
                {detail.handledAt ? <span className="text-gray-400 font-normal"> · {formatDateTime(detail.handledAt, '')}</span> : null}
              </dd>
            </div>
          </dl>

          <div className="border-t border-gray-100 pt-4">
            <h3 className="text-xs font-semibold text-gray-500 mb-2">问题原文</h3>
            <p className="text-sm text-gray-800 leading-relaxed whitespace-pre-wrap">{detail.question}</p>
          </div>

          <div className="border-t border-gray-100 pt-4">
            <h3 className="text-xs font-semibold text-gray-500 mb-2">
              给买家的回复
              {detail.repliedAt ? (
                <span className="ml-2 font-normal text-gray-400">{formatDateTime(detail.repliedAt, '')}</span>
              ) : null}
            </h3>
            {detail.replyContent ? (
              <p className="text-sm text-gray-800 leading-relaxed whitespace-pre-wrap">{detail.replyContent}</p>
            ) : (
              <p className="text-sm text-gray-400">尚未回复。买家在门户「个人中心 → 我的工单」里只能看到已发出的回复。</p>
            )}
          </div>

          {detail.aiSummary ? (
            <div className="border-t border-gray-100 pt-4">
              <h3 className="text-xs font-semibold text-gray-500 mb-2">AI 归类摘要</h3>
              <p className="text-sm text-gray-700 leading-relaxed">{detail.aiSummary}</p>
            </div>
          ) : null}
        </section>

        <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 md:p-6 space-y-4">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg"><CheckCircle className="w-5 h-5" /></div>
            <div>
              <h3 className="text-base font-semibold text-gray-900">处理</h3>
              <p className="text-xs text-gray-500">
                处理状态与处理人仅后台可见；「回复买家」会出现在门户「我的工单」里，请按对外口径写。
              </p>
            </div>
          </div>

          <PermissionGate
            permission="ai:ticket:handle"
            fallback={<p className="text-xs text-gray-500">当前账号只有查看权限，处理操作需由拥有「客服工单处理」权限的账号完成。</p>}
          >
            <div className="space-y-3">
              <label className="flex flex-col gap-1 text-xs text-gray-500 max-w-xs">
                处理结果
                <select
                  id="ai-ticket-handle-status"
                  name="handleStatus"
                  value={handleStatus}
                  onChange={(event) => setHandleStatus(event.target.value as TicketStatus)}
                  aria-label="工单处理结果"
                  className="h-9 px-3 rounded-lg border border-[#E2E8F0] bg-white text-sm text-gray-700 outline-none focus:border-blue-500"
                >
                  <option value="PENDING">待处理</option>
                  <option value="PROCESSING">处理中</option>
                  <option value="CLOSED">已关闭</option>
                </select>
              </label>
              <label className="flex flex-col gap-1 text-xs text-gray-500">
                回复买家（门户可见）
                <textarea
                  id="ai-ticket-handle-reply"
                  name="reply"
                  value={handleReply}
                  onChange={(event) => setHandleReply(event.target.value)}
                  rows={3}
                  placeholder="写清楚结论与后续动作，买家会在「我的工单」里看到这段话；留空表示不修改已发出的回复"
                  className="w-full px-3 py-2 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500 resize-y"
                />
              </label>
              <label className="flex flex-col gap-1 text-xs text-gray-500">
                处理备注（仅后台可见）
                <textarea
                  id="ai-ticket-handle-note"
                  name="handleNote"
                  value={handleNote}
                  onChange={(event) => setHandleNote(event.target.value)}
                  rows={3}
                  placeholder="记录沟通过程、结论，或后续需要跟进的事项（仅后台可见）"
                  className="w-full px-3 py-2 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500 resize-y"
                />
              </label>
              <button
                type="button"
                disabled={saving}
                onClick={() => void submitHandle()}
                className="h-9 px-4 bg-[#2563EB] text-white rounded-lg text-sm font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5 disabled:opacity-60"
              >
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                {saving ? '保存中…' : '保存处理结果'}
              </button>
            </div>
          </PermissionGate>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">客服工单</h2>
          <span className="text-xs bg-blue-50 text-blue-700 font-semibold px-2 py-0.5 rounded-full border border-blue-200 inline-flex items-center gap-1">
            <Headphones className="w-3 h-3" />转人工
          </span>
        </div>
        <p className="text-xs md:text-sm text-[#434655] mt-0.5">
          智能客服答不上来的问题会落成工单，这里是它们的处理入口。
        </p>
      </div>

      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-4 flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px] max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
          <input
            type="search"
            id="ai-ticket-keyword"
            name="keyword"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                setKeywordFilter(keywordInput);
                setCurrent(1);
              }
            }}
            placeholder="搜索联系方式或问题关键字…"
            className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none focus:border-blue-500"
          />
        </div>
        <select
          id="ai-ticket-status"
          name="status"
          value={statusInput}
          onChange={(event) => {
            setStatusInput(event.target.value);
            setStatusFilter(event.target.value);
            setCurrent(1);
          }}
          aria-label="工单处理状态"
          className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
        >
          {STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <button
          type="button"
          onClick={() => {
            setKeywordFilter(keywordInput);
            setCurrent(1);
          }}
          className="h-[36px] px-3 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-blue-700"
        >
          查询
        </button>
        <button
          type="button"
          onClick={() => void loadTickets(current)}
          className="h-[36px] px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5"
        >
          <RefreshCw className="w-4 h-4" />刷新
        </button>
      </div>

      <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5">
        <div className="overflow-x-auto">
          {loadError ? (
            <div className="py-10 text-center text-sm text-red-700" role="alert">
              <AlertCircle className="w-6 h-6 mx-auto mb-2" />
              <p>{loadError}</p>
              <button
                type="button"
                onClick={() => void loadTickets(current)}
                className="mt-3 inline-flex items-center gap-1.5 text-blue-700 hover:underline"
              >
                <RefreshCw className="w-4 h-4" />重新加载
              </button>
            </div>
          ) : loading ? (
            <p className="py-10 text-center text-sm text-gray-500" role="status">
              <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />工单加载中…
            </p>
          ) : records.length === 0 ? (
            <p className="py-10 text-center text-sm text-gray-400" role="status">暂无工单</p>
          ) : (
            <table className="w-full text-xs">
              <thead>
                <tr className="text-left text-gray-500 border-b">
                  <th className="py-2 pr-3">工单号</th>
                  <th className="py-2 pr-3">联系方式</th>
                  <th className="py-2 pr-3">问题</th>
                  <th className="py-2 pr-3">状态</th>
                  <th className="py-2 pr-3">提交时间</th>
                  <th className="py-2 pr-3">处理人</th>
                  <th className="py-2">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((item) => {
                  const meta = statusMeta(item.status);
                  return (
                    <tr key={item.id} className="border-b border-gray-50 hover:bg-slate-50/60">
                      <td className="py-2.5 pr-3 whitespace-nowrap font-mono text-gray-800">{item.ticketNo || `#${item.id}`}</td>
                      <td className="py-2.5 pr-3 whitespace-nowrap text-gray-700">{item.contact || '-'}</td>
                      <td className="py-2.5 pr-3 text-gray-700 max-w-[320px]">
                        <span className="line-clamp-2">{item.question}</span>
                      </td>
                      <td className="py-2.5 pr-3 whitespace-nowrap">
                        <span className={`px-1.5 py-0.5 rounded border font-semibold ${meta.chip}`}>{meta.label}</span>
                      </td>
                      <td className="py-2.5 pr-3 whitespace-nowrap text-gray-600">{formatDateTime(item.createdAt, '-')}</td>
                      <td className="py-2.5 pr-3 whitespace-nowrap text-gray-600">{item.handlerName || '-'}</td>
                      <td className="py-2.5 whitespace-nowrap">
                        <button
                          type="button"
                          onClick={() => openDetail(item)}
                          className="font-semibold text-blue-700 hover:underline"
                        >
                          查看
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {!loadError && !loading && (
          <div className="flex flex-wrap items-center justify-between gap-2 mt-4 pt-3 border-t border-gray-100 text-xs text-gray-500">
            <span>共 {total} 条，第 {Math.min(current, totalPages)} / {totalPages} 页</span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setCurrent((page) => Math.max(1, page - 1))}
                disabled={current <= 1}
                className="h-8 px-3 rounded-lg border border-[#E2E8F0] font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                上一页
              </button>
              <button
                type="button"
                onClick={() => setCurrent((page) => Math.min(totalPages, page + 1))}
                disabled={current >= totalPages}
                className="h-8 px-3 rounded-lg border border-[#E2E8F0] font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                下一页
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
};
