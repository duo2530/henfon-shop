import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AiAgentStatus,
  BackendAiAgentStatsBoard,
  BackendAiAgentStatsDetail,
  BackendAiAgentStatsRow,
  getAiAgentStatsBoard,
  getAiAgentStatsDetail,
} from '../../api/adminApi';
import { AlertCircle, ArrowLeft, BarChart3, Loader2, RefreshCw, Star } from 'lucide-react';

/** 可选统计窗口。0 表示不限时间。 */
const WINDOWS: Array<{ days: number; label: string }> = [
  { days: 7, label: '近 7 天' },
  { days: 30, label: '近 30 天' },
  { days: 90, label: '近 90 天' },
  { days: 0, label: '全部' },
];

/** 坐席状态的展示配色，与排班页保持一致。 */
const STATUS_DOT: Record<AiAgentStatus, string> = {
  ONLINE: 'bg-emerald-500',
  BREAK: 'bg-amber-500',
  OFFLINE: 'bg-gray-300',
};

const STATUS_LABEL: Record<AiAgentStatus, string> = {
  ONLINE: '在线',
  BREAK: '小休',
  OFFLINE: '离线',
};

/**
 * 把秒数说成人话。
 *
 * 超过一小时就带上分钟：只看"3 小时"分不出 3 小时 05 分和 3 小时 55 分，而这两种情况
 * 在客服的日常里差别不小。
 *
 * @param seconds 秒数
 * @returns 形如「3 小时 20 分」的文本
 */
function formatDuration(seconds?: number | null): string {
  if (seconds == null || seconds <= 0) return '—';
  if (seconds < 60) return '少于 1 分钟';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`;
}

/**
 * 百分比保留一位小数，没有数据时显示占位符。
 *
 * @param rate 百分比数值
 * @returns 展示文本
 */
function formatRate(rate?: number | null): string {
  return rate == null ? '—' : `${rate.toFixed(1)}%`;
}

/**
 * 把后端时间截成「月-日 时:分」。
 *
 * @param value ISO 时间字符串
 * @returns 展示文本
 */
function formatMoment(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(5, 16) : '—';
}

/**
 * 客服统计。
 *
 * 按人看接待量、服务时长与评价：接待量与时长相乘能看出排班是不是把活压在少数人身上，
 * 好评差评率则用来分辨"接得多"和"接得好"。两者不合并成一个分数，理由见后端
 * AiAgentStatsRow 的说明。
 *
 * 口径上有两处刻意的区分：服务时长与接待量只统计已结束的人工服务，进行中的会话单列在
 * 「在接」里；评价为空显示「—」而不是 0%，没被评过和被评了 0 分不是一回事。
 *
 * 详情是列表内的第二层视图而不是独立页面：看来源是"列表里某个人不对劲"，看完要立刻回到
 * 同一张表继续看下一个人，中间隔着一次菜单跳转就把这两个动作拆散了。返回时统计窗口原样保留。
 *
 * @author Henfon
 * @date 2026-09-21
 */
export const AiAgentStatsView: React.FC = () => {
  const [days, setDays] = useState(30);
  const [board, setBoard] = useState<BackendAiAgentStatsBoard | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [detailAgentId, setDetailAgentId] = useState<number | null>(null);
  const [detail, setDetail] = useState<BackendAiAgentStatsDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const load = useCallback(async (window: number) => {
    setLoading(true);
    try {
      setBoard(await getAiAgentStatsBoard(window));
      setLoadError(null);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '客服统计加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(days);
  }, [days, load]);

  const loadDetail = useCallback(async (agentId: number, window: number) => {
    setDetailLoading(true);
    try {
      setDetail(await getAiAgentStatsDetail(agentId, window));
      setDetailError(null);
    } catch (error) {
      setDetail(null);
      setDetailError(error instanceof Error ? error.message : '客服详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (detailAgentId == null) {
      return;
    }
    void loadDetail(detailAgentId, days);
  }, [detailAgentId, days, loadDetail]);

  const rows = board?.agents || [];

  /** 表头汇总卡：接待量与评价都是加权合计，不是各人平均再平均。 */
  const summary = useMemo(() => {
    let online = 0;
    let served = 0;
    let seconds = 0;
    let ratings = 0;
    let satisfied = 0;
    let unsatisfied = 0;
    rows.forEach((row) => {
      if (row.status === 'ONLINE') online += 1;
      served += row.servedCount;
      seconds += row.totalServeSeconds;
      ratings += row.ratingCount;
      satisfied += row.satisfied;
      unsatisfied += row.unsatisfied;
    });
    return {
      online,
      served,
      averageSeconds: served === 0 ? null : Math.round(seconds / served),
      ratings,
      satisfactionRate: ratings === 0 ? null : Math.round((satisfied * 1000) / ratings) / 10,
      unsatisfied,
    };
  }, [rows]);

  const windowLabel = WINDOWS.find((item) => item.days === days)?.label || `近 ${days} 天`;
  const detailRow = detail?.summary;
  const trendMax = Math.max(1, ...(detail?.trend.map((point) => point.served) ?? [0]));

  const renderRow = (row: BackendAiAgentStatsRow) => (
    <tr key={row.agentId} className="border-t border-[#E2E8F0]">
      <th scope="row" className="px-3 py-2.5 text-left align-middle">
        <button
          type="button"
          onClick={() => setDetailAgentId(row.agentId)}
          className="flex items-center gap-2 text-left group"
        >
          <span className={`w-2 h-2 rounded-full shrink-0 ${STATUS_DOT[row.status]}`} aria-hidden="true" />
          <span className="min-w-0">
            <span className="block text-xs font-semibold text-gray-900 truncate group-hover:text-blue-700 group-hover:underline">
              {row.agentName}
            </span>
            <span className="block text-[11px] text-gray-400">
              {STATUS_LABEL[row.status]}
              {row.servingCount > 0 && ` · 在接 ${row.servingCount} 单`}
            </span>
          </span>
        </button>
      </th>
      <td className="px-3 py-2.5 text-right align-middle text-xs text-gray-700 tabular-nums">
        {row.servedCount}
      </td>
      <td className="px-3 py-2.5 text-right align-middle text-xs text-gray-700 tabular-nums">
        {formatDuration(row.totalServeSeconds)}
      </td>
      <td className="px-3 py-2.5 text-right align-middle text-xs text-gray-700 tabular-nums">
        {formatDuration(row.averageServeSeconds)}
      </td>
      <td className="px-3 py-2.5 text-right align-middle text-xs text-gray-700 tabular-nums">
        {row.ratingCount === 0 ? (
          '—'
        ) : (
          <>
            {row.ratingAverage != null ? row.ratingAverage.toFixed(1) : '—'}
            <span className="ml-1 text-[11px] text-gray-400">({row.ratingCount})</span>
          </>
        )}
      </td>
      <td className="px-3 py-2.5 align-middle">
        <span className="flex items-center justify-end gap-2">
          <span className="h-1.5 w-20 rounded-full bg-slate-100 overflow-hidden" aria-hidden="true">
            <span
              className="block h-full rounded-full bg-emerald-500"
              style={{ width: `${row.satisfactionRate ?? 0}%` }}
            />
          </span>
          <span className="text-xs text-gray-700 tabular-nums w-12 text-right">
            {formatRate(row.satisfactionRate)}
          </span>
        </span>
      </td>
      <td className="px-3 py-2.5 text-right align-middle text-xs tabular-nums">
        <span className={row.unsatisfied > 0 ? 'text-rose-600 font-semibold' : 'text-gray-500'}>
          {formatRate(row.dissatisfactionRate)}
        </span>
      </td>
      <td className="px-3 py-2.5 text-right align-middle text-xs text-gray-500 tabular-nums">
        {formatMoment(row.lastServedAt)}
      </td>
    </tr>
  );

  /** 详情页的小指标卡。 */
  const renderMetric = (label: string, value: string, hint?: string) => (
    <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs px-4 py-3">
      <p className="text-[11px] text-gray-500">{label}</p>
      <p className="mt-1 text-lg font-bold text-[#191C1E] tabular-nums">{value}</p>
      {hint && <p className="text-[11px] text-gray-400">{hint}</p>}
    </div>
  );

  const renderDetail = () => (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setDetailAgentId(null)}
            className="h-9 px-3 rounded-lg border border-[#E2E8F0] bg-white text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5"
          >
            <ArrowLeft className="w-4 h-4" />返回列表
          </button>
          <span className="text-xs text-gray-400">客服统计</span>
          <span className="text-xs text-gray-300">/</span>
          <span className="text-sm font-semibold text-gray-900">
            {detail?.summary.agentName || '客服详情'}
          </span>
          {detailRow && (
            <span className="inline-flex items-center gap-1.5 text-[11px] text-gray-500">
              <span className={`w-2 h-2 rounded-full ${STATUS_DOT[detailRow.status]}`} aria-hidden="true" />
              {STATUS_LABEL[detailRow.status]}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border border-[#E2E8F0] bg-white overflow-hidden">
            {WINDOWS.map((item) => (
              <button
                key={item.days}
                type="button"
                onClick={() => setDays(item.days)}
                aria-pressed={days === item.days}
                className={`h-9 px-3 text-sm transition-colors ${
                  days === item.days
                    ? 'bg-[#2563EB] text-white font-semibold'
                    : 'text-gray-600 hover:bg-gray-50'
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
          <button
            type="button"
            disabled={detailAgentId == null || detailLoading}
            onClick={() => detailAgentId != null && void loadDetail(detailAgentId, days)}
            className="h-9 px-3 rounded-lg border border-[#E2E8F0] bg-white text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5 disabled:opacity-60"
          >
            <RefreshCw className={`w-4 h-4 ${detailLoading ? 'animate-spin' : ''}`} />刷新
          </button>
        </div>
      </div>

      {detailError && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
          <AlertCircle className="w-4 h-4 inline mr-1.5" />{detailError}
        </div>
      )}

      {detailLoading && !detail ? (
        <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs px-4 py-10 text-center text-xs text-gray-400" role="status">
          <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />加载中…
        </div>
      ) : detail && detailRow ? (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-3">
            {renderMetric('接待会话', String(detailRow.servedCount), `${windowLabel}内已结束`)}
            {renderMetric('累计服务时长', formatDuration(detailRow.totalServeSeconds), '只计已结束会话')}
            {renderMetric('平均服务时长', formatDuration(detailRow.averageServeSeconds), '按会话加权')}
            {renderMetric('当前在接', String(detailRow.servingCount), '不受统计窗口影响')}
            {renderMetric(
              '好评率',
              formatRate(detailRow.satisfactionRate),
              `${detailRow.ratingCount} 条评价 · 差评 ${detailRow.unsatisfied} 条`,
            )}
            {renderMetric(
              '排班工时',
              `${detail.scheduledHours} 小时`,
              `排班表启用 ${detail.scheduledDays} 天`,
            )}
          </div>

          <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs p-4">
            <h3 className="text-sm font-semibold text-gray-900">按天接待量</h3>
            <p className="mt-0.5 text-[11px] text-gray-400">
              {detail.trend.length > 0
                ? `${detail.trend[0].date.slice(5)} 至 ${detail.trend[detail.trend.length - 1].date.slice(5)}，柱子高度按最高的一天换算`
                : ''}
            </p>
            {detail.trend.length === 0 ? (
              <p className="py-6 text-center text-xs text-gray-400" role="status">这段时间没有人工服务记录</p>
            ) : (
              <div className="mt-3 flex items-end gap-[3px] h-24" role="img" aria-label="按天接待量趋势">
                {detail.trend.map((point) => (
                  <span
                    key={point.date}
                    title={`${point.date}：${point.served} 次`}
                    style={{ height: `${Math.max(point.served === 0 ? 1 : 6, (point.served / trendMax) * 100)}%` }}
                    className={`flex-1 rounded-t ${point.served === 0 ? 'bg-slate-200' : 'bg-blue-500'}`}
                  />
                ))}
              </div>
            )}
          </div>

          <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse">
              <caption className="sr-only">该客服最近的人工会话明细</caption>
              <thead>
                <tr className="bg-slate-50">
                  <th scope="col" className="px-3 py-2.5 text-left text-[11px] font-semibold text-gray-500">买家</th>
                  <th scope="col" className="px-3 py-2.5 text-left text-[11px] font-semibold text-gray-500">会话</th>
                  <th scope="col" className="px-3 py-2.5 text-left text-[11px] font-semibold text-gray-500">接入时间</th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500">服务时长</th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500">评价</th>
                </tr>
              </thead>
              <tbody>
                {detail.sessions.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-xs text-gray-400" role="status">
                      这段时间没有人工服务记录
                    </td>
                  </tr>
                ) : (
                  detail.sessions.map((session) => (
                    <tr key={session.conversationId} className="border-t border-[#E2E8F0]">
                      <td className="px-3 py-2.5 text-xs text-gray-800">
                        {session.memberName || '未登录访客'}
                      </td>
                      <td className="px-3 py-2.5 text-xs text-gray-600 max-w-[280px] truncate">
                        {session.title || '未命名会话'}
                      </td>
                      <td className="px-3 py-2.5 text-xs text-gray-500 tabular-nums">
                        {formatMoment(session.joinedAt)}
                      </td>
                      <td className="px-3 py-2.5 text-right text-xs text-gray-700 tabular-nums">
                        {session.endedAt ? formatDuration(session.serveSeconds) : '进行中'}
                      </td>
                      <td className="px-3 py-2.5 text-right text-xs tabular-nums">
                        {session.score == null ? (
                          <span className="text-gray-400">未评价</span>
                        ) : (
                          <span className="text-amber-600 font-semibold">{session.score} 星</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs p-4">
            <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <Star className="w-4 h-4 text-amber-500" />最近评价
            </h3>
            <p className="mt-0.5 text-[11px] text-gray-400">
              评价按全部历史统计，不随上面的窗口变化，与列表里的好评率同一口径。
            </p>
            {detail.ratings.length === 0 ? (
              <p className="py-6 text-center text-xs text-gray-400" role="status">还没有收到评价</p>
            ) : (
              <ul className="mt-2 space-y-2.5">
                {detail.ratings.map((rating) => (
                  <li key={rating.conversationId} className="border-b border-gray-50 last:border-0 pb-2 last:pb-0">
                    <div className="flex items-center gap-1.5">
                      <span className="text-[11px] font-semibold text-amber-600">{rating.score} 星</span>
                      <span className="text-[11px] text-gray-400">{formatMoment(rating.createdAt)}</span>
                    </div>
                    {rating.tags.length > 0 && (
                      <p className="mt-1 flex flex-wrap gap-1">
                        {rating.tags.map((tag) => (
                          <span key={tag} className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-gray-600">{tag}</span>
                        ))}
                      </p>
                    )}
                    {rating.comment && (
                      <p className="mt-1 text-[11px] text-gray-600 leading-relaxed">{rating.comment}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      ) : null}
    </>
  );

  return (
    <div className="space-y-5 animate-in fade-in-50 duration-200">
      {detailAgentId == null ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">客服统计</h2>
              <p className="text-xs md:text-sm text-[#434655] mt-0.5">
                按人看接待量、服务时长与评价。接待量与时长只统计已结束的人工服务，进行中的会话列在「在接」里。
              </p>
            </div>
            <div className="flex items-center gap-2">
              <div className="flex rounded-lg border border-[#E2E8F0] bg-white overflow-hidden">
                {WINDOWS.map((item) => (
                  <button
                    key={item.days}
                    type="button"
                    onClick={() => setDays(item.days)}
                    aria-pressed={days === item.days}
                    className={`h-9 px-3 text-sm transition-colors ${
                      days === item.days
                        ? 'bg-[#2563EB] text-white font-semibold'
                        : 'text-gray-600 hover:bg-gray-50'
                    }`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
              <button
                type="button"
                onClick={() => void load(days)}
                className="h-9 px-3 rounded-lg border border-[#E2E8F0] bg-white text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5"
              >
                <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />刷新
              </button>
            </div>
          </div>

          {loadError && (
            <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
              <AlertCircle className="w-4 h-4 inline mr-1.5" />{loadError}
            </div>
          )}

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs px-4 py-3">
              <p className="text-[11px] text-gray-500">客服人数</p>
              <p className="mt-1 text-lg font-bold text-[#191C1E] tabular-nums">{rows.length}</p>
              <p className="text-[11px] text-gray-400">其中在线 {summary.online} 人</p>
            </div>
            <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs px-4 py-3">
              <p className="text-[11px] text-gray-500">接待会话</p>
              <p className="mt-1 text-lg font-bold text-[#191C1E] tabular-nums">{summary.served}</p>
              <p className="text-[11px] text-gray-400">{windowLabel}内已结束的人工服务</p>
            </div>
            <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs px-4 py-3">
              <p className="text-[11px] text-gray-500">平均服务时长</p>
              <p className="mt-1 text-lg font-bold text-[#191C1E] tabular-nums">
                {formatDuration(summary.averageSeconds)}
              </p>
              <p className="text-[11px] text-gray-400">按会话加权</p>
            </div>
            <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs px-4 py-3">
              <p className="text-[11px] text-gray-500">整体好评率</p>
              <p className="mt-1 text-lg font-bold text-[#191C1E] tabular-nums">
                {formatRate(summary.satisfactionRate)}
              </p>
              <p className="text-[11px] text-gray-400">
                {summary.ratings === 0 ? '暂无评价' : `${summary.ratings} 条评价 · 差评 ${summary.unsatisfied} 条`}
              </p>
            </div>
          </div>

          <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs overflow-x-auto">
            <table className="w-full min-w-[960px] border-collapse">
              <caption className="sr-only">客服服务记录统计表，点客服名字看详情</caption>
              <thead>
                <tr className="bg-slate-50">
                  <th scope="col" className="px-3 py-2.5 text-left text-[11px] font-semibold text-gray-500 w-[190px]">
                    客服
                  </th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500">
                    接待会话
                  </th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500">
                    累计服务时长
                  </th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500">
                    平均服务时长
                  </th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500">
                    评价
                  </th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500 w-[170px]">
                    好评率
                  </th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500">
                    差评率
                  </th>
                  <th scope="col" className="px-3 py-2.5 text-right text-[11px] font-semibold text-gray-500">
                    最近服务
                  </th>
                </tr>
              </thead>
              <tbody>
                {loading && rows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-10 text-center text-xs text-gray-400" role="status">
                      <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />加载中…
                    </td>
                  </tr>
                ) : rows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-10 text-center text-xs text-gray-400" role="status">
                      还没有可统计的客服。持有「客服工作台」权限的账号会出现在这里。
                    </td>
                  </tr>
                ) : (
                  rows.map(renderRow)
                )}
              </tbody>
            </table>
          </div>
        </>
      ) : (
        renderDetail()
      )}

      <div className="rounded-xl border border-[#E2E8F0] bg-slate-50 px-4 py-3 text-xs text-gray-600">
        <BarChart3 className="w-4 h-4 inline mr-1.5 text-gray-400" />
        好评指 4 星及以上，差评指 2 星及以下。评价只统计人工服务结束后的满意度评价，智能客服的问答不计入。
      </div>
    </div>
  );
};
