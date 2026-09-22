import React, { useCallback, useEffect, useState } from 'react';
import { AlertCircle, Headphones, Loader2, RefreshCw } from 'lucide-react';
import { fetchPortalAiTickets, PortalAiTicketRecord } from '../api/portalApi';

const PAGE_SIZE = 10;

/** 状态展示口径，与后台「客服工单」页保持一致，买家看到的说法与客服说的一致。 */
const STATUS_META: Record<PortalAiTicketRecord['status'], { label: string; chip: string }> = {
  PENDING: { label: '待处理', chip: 'bg-amber-50 text-amber-700 border-amber-200' },
  PROCESSING: { label: '处理中', chip: 'bg-blue-50 text-blue-700 border-blue-200' },
  CLOSED: { label: '已关闭', chip: 'bg-zinc-100 text-zinc-600 border-zinc-200' },
};

function formatTime(value?: string): string {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

/**
 * 个人中心「我的工单」。
 *
 * 只做一件事：把买家提交过的工单与客服的回复摆出来。以前工单提交完就断了——买家手里只有
 * 一个编号，看不到有没有人处理、处理结果是什么，只能反复打电话进来问。
 *
 * 列表按提交时间倒序，展开即取详情：工单字段少，逐条展开比"列表 → 详情页"少一次跳转。
 *
 * @author Henfon
 * @date 2026-09-21
 */
export const MyTicketsPanel: React.FC = () => {
  const [records, setRecords] = useState<PortalAiTicketRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const load = useCallback(async (page: number) => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await fetchPortalAiTickets(page, PAGE_SIZE);
      const list = data.records || [];
      setRecords(list);
      setTotal(data.total || 0);
      setTotalPages(Math.max(data.pages || 1, 1));
      setCurrent(data.current || page);
      // 默认展开最新一条：买家进来最关心的是"有没有人回我"。
      setExpandedId(list.length ? list[0].id : null);
    } catch (error) {
      setRecords([]);
      setTotal(0);
      setTotalPages(1);
      setLoadError(error instanceof Error ? error.message : '工单加载失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(1);
  }, [load]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Headphones className="w-4 h-4 text-zinc-700" />
          <h2 className="text-sm font-bold text-zinc-900">我的工单 ({total})</h2>
        </div>
        <button
          type="button"
          onClick={() => void load(current)}
          className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-zinc-600 hover:border-zinc-300 hover:text-zinc-900"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />刷新
        </button>
      </div>

      {loadError ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-6 text-center text-xs text-rose-700" role="alert">
          <AlertCircle className="w-5 h-5 mx-auto mb-2" />
          <p>{loadError}</p>
        </div>
      ) : loading && !records.length ? (
        <div className="rounded-2xl border border-zinc-200 bg-white py-10 text-center text-xs text-zinc-500" role="status">
          <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />工单加载中…
        </div>
      ) : records.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-zinc-200 bg-zinc-50 py-10 text-center space-y-1.5">
          <Headphones className="w-8 h-8 text-zinc-300 mx-auto" />
          <p className="text-xs font-medium text-zinc-500">还没有提交过工单</p>
          <p className="text-[11px] text-zinc-400">在客服窗口点「转人工」留言后，记录会出现在这里</p>
        </div>
      ) : (
        <ul className="space-y-3">
          {records.map((ticket) => {
            const meta = STATUS_META[ticket.status] || { label: ticket.status, chip: 'bg-zinc-100 text-zinc-600 border-zinc-200' };
            const expanded = expandedId === ticket.id;
            return (
              <li key={ticket.id} className="rounded-2xl border border-zinc-200 bg-white overflow-hidden">
                <button
                  type="button"
                  aria-expanded={expanded}
                  onClick={() => setExpandedId(expanded ? null : ticket.id)}
                  className="w-full px-4 py-3 text-left hover:bg-zinc-50/70"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-mono text-xs text-zinc-800">{ticket.ticketNo || `#${ticket.id}`}</span>
                    <span className={`rounded-full border px-2 py-0.5 text-[11px] font-semibold ${meta.chip}`}>{meta.label}</span>
                  </div>
                  <p className="mt-1.5 line-clamp-2 text-xs text-zinc-600">{ticket.question}</p>
                  <p className="mt-1 text-[11px] text-zinc-400">
                    提交于 {formatTime(ticket.createdAt)}
                    {ticket.repliedAt ? ` · 客服回复于 ${formatTime(ticket.repliedAt)}` : ' · 客服尚未回复'}
                  </p>
                </button>

                {expanded && (
                  <div className="border-t border-zinc-100 px-4 py-3 space-y-3">
                    <div>
                      <h3 className="mb-1 text-[11px] font-semibold text-zinc-400">我的问题</h3>
                      <p className="text-xs leading-relaxed text-zinc-700 whitespace-pre-wrap">{ticket.question}</p>
                    </div>
                    <div>
                      <h3 className="mb-1 text-[11px] font-semibold text-zinc-400">客服回复</h3>
                      {ticket.reply ? (
                        <p className="whitespace-pre-wrap rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs leading-relaxed text-zinc-800">
                          {ticket.reply}
                        </p>
                      ) : (
                        <p className="text-xs text-zinc-400">客服还没有回复。</p>
                      )}
                    </div>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {!loadError && !loading && totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-zinc-500">
          <span>共 {total} 条，第 {Math.min(current, totalPages)} / {totalPages} 页</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void load(Math.max(1, current - 1))}
              disabled={current <= 1}
              className="rounded-lg border border-zinc-200 bg-white px-3 py-1.5 font-semibold text-zinc-600 hover:border-zinc-300 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              上一页
            </button>
            <button
              type="button"
              onClick={() => void load(Math.min(totalPages, current + 1))}
              disabled={current >= totalPages}
              className="rounded-lg border border-zinc-200 bg-white px-3 py-1.5 font-semibold text-zinc-600 hover:border-zinc-300 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              下一页
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
