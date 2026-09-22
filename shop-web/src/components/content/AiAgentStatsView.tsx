import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AiAgentStatus,
  BackendAiAgentStatsBoard,
  BackendAiAgentStatsRow,
  getAiAgentStatsBoard,
} from '../../api/adminApi';
import { AlertCircle, BarChart3, Loader2, RefreshCw } from 'lucide-react';

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
 * @author Henfon
 * @date 2026-09-21
 */
export const AiAgentStatsView: React.FC = () => {
  const [days, setDays] = useState(30);
  const [board, setBoard] = useState<BackendAiAgentStatsBoard | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

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

  const renderRow = (row: BackendAiAgentStatsRow) => (
    <tr key={row.agentId} className="border-t border-[#E2E8F0]">
      <th scope="row" className="px-3 py-2.5 text-left align-middle">
        <span className="flex items-center gap-2">
          <span className={`w-2 h-2 rounded-full shrink-0 ${STATUS_DOT[row.status]}`} aria-hidden="true" />
          <span className="min-w-0">
            <span className="block text-xs font-semibold text-gray-900 truncate">{row.agentName}</span>
            <span className="block text-[11px] text-gray-400">
              {STATUS_LABEL[row.status]}
              {row.servingCount > 0 && ` · 在接 ${row.servingCount} 单`}
            </span>
          </span>
        </span>
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

  return (
    <div className="space-y-5 animate-in fade-in-50 duration-200">
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
          <caption className="sr-only">客服服务记录统计表</caption>
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

      <div className="rounded-xl border border-[#E2E8F0] bg-slate-50 px-4 py-3 text-xs text-gray-600">
        <BarChart3 className="w-4 h-4 inline mr-1.5 text-gray-400" />
        好评指 4 星及以上，差评指 2 星及以下。评价只统计人工服务结束后的满意度评价，智能客服的问答不计入。
      </div>
    </div>
  );
};
