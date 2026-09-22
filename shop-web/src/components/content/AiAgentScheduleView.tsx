import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import {
  AiAgentStatus,
  AiSchedulePlan,
  AiSchedulePlanRequest,
  BackendAiSchedule,
  BackendAiScheduleAgent,
  applyAiAgentSchedulePlan,
  deleteAiAgentSchedule,
  getAiAgentScheduleBoard,
  parseAiAgentSchedulePlan,
  planAiAgentSchedule,
  saveAiAgentSchedule,
} from '../../api/adminApi';
import {
  AlertCircle,
  CalendarClock,
  CalendarPlus,
  Check,
  Loader2,
  RefreshCw,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';

/** 星期表头，下标 0 对应周一。 */
const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

/** 全部星期，一键排班默认整周。 */
const ALL_WEEKDAYS = [1, 2, 3, 4, 5, 6, 7];

/** 新排班的默认时段，上班族最常见的一段。 */
const DEFAULT_START = '09:00';
const DEFAULT_END = '18:00';

/** 坐席状态的展示配色。 */
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

/** 排班计划里三种变动的说法。 */
const CHANGE_LABEL: Record<AiSchedulePlan['items'][number]['change'], string> = {
  ADD: '新增',
  UPDATE: '修改',
  REMOVE: '删除',
};

/** 正在编辑的格子。id 为空表示新建。 */
interface EditingCell {
  id?: number;
  agentId: number;
  agentName: string;
  weekday: number;
  startTime: string;
  endTime: string;
  enabled: boolean;
}

/** 一键排班的参数表单。数字用字符串存，方便留出「不限制」这个空值。 */
interface PlanForm {
  startTime: string;
  endTime: string;
  perDay: string;
  weekdays: number[];
  maxShiftHours: string;
  maxShiftsPerAgent: string;
  clearUncovered: boolean;
}

/** 一键排班的默认参数：与后端缺省值一致，点开就能直接生成。 */
const DEFAULT_PLAN_FORM: PlanForm = {
  startTime: DEFAULT_START,
  endTime: DEFAULT_END,
  perDay: '1',
  weekdays: ALL_WEEKDAYS,
  maxShiftHours: '',
  maxShiftsPerAgent: '',
  clearUncovered: false,
};

/**
 * 把 HH:mm:ss 截成 HH:mm。
 *
 * @param time 时间字符串
 * @returns 形如 09:00 的文本
 */
function formatClock(time?: string | null): string {
  return time ? time.slice(0, 5) : '';
}

/**
 * 算两个时刻之间的小时数。
 *
 * @param start 开始时间
 * @param end 结束时间
 * @returns 小时数，非法区间返回 0
 */
function hoursBetween(start: string, end: string): number {
  const [sh, sm] = start.split(':').map(Number);
  const [eh, em] = end.split(':').map(Number);
  if ([sh, sm, eh, em].some((value) => Number.isNaN(value))) return 0;
  const minutes = (eh * 60 + em) - (sh * 60 + sm);
  return minutes > 0 ? minutes / 60 : 0;
}

/**
 * 客服排班。
 *
 * 排班以周为单位维护：客服的作息基本是按周固定的，按日期排会让每周都要重新排一遍。
 * 星期几用 1-7 表示（与后端的 weekday 一致），一天一段班——需要早晚两班倒时再放开这个约束，
 * 同时把格子改成多段编辑。
 *
 * 排班不决定"有没有客服在线"：那是坐席上线状态与心跳的判断，两者不一致时以事实为准。
 * 排班决定的是对外的服务时段提示——时段外买家看到的是"非服务时间"，时段内没人上线看到的是
 * "客服暂时不在线"，这两句话对应完全不同的行动建议。
 *
 * @author Henfon
 * @date 2026-09-21
 */
export const AiAgentScheduleView: React.FC = () => {
  const { showToast, confirm: confirmDialog } = useAdmin();

  const [agents, setAgents] = useState<BackendAiScheduleAgent[]>([]);
  const [schedules, setSchedules] = useState<BackendAiSchedule[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState<EditingCell | null>(null);
  const [saving, setSaving] = useState(false);

  const [planOpen, setPlanOpen] = useState(false);
  const [planForm, setPlanForm] = useState<PlanForm>(DEFAULT_PLAN_FORM);
  const [planText, setPlanText] = useState('');
  const [plan, setPlan] = useState<AiSchedulePlan | null>(null);
  const [planError, setPlanError] = useState<string | null>(null);
  const [parsing, setParsing] = useState(false);
  const [planning, setPlanning] = useState(false);
  const [applying, setApplying] = useState(false);

  useBodyScrollLock(planOpen);

  const closePlan = useCallback(() => {
    setPlanOpen(false);
    setPlan(null);
    setPlanError(null);
    setPlanText('');
    setPlanForm(DEFAULT_PLAN_FORM);
  }, []);

  // Esc 关掉弹框。捕获阶段监听：排班页里还有输入框，事件冒泡到它们之前先拦下来。
  useEffect(() => {
    if (!planOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closePlan();
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [planOpen, closePlan]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const board = await getAiAgentScheduleBoard();
      setAgents(board.agents || []);
      setSchedules(board.schedules || []);
      setLoadError(null);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '排班加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  /** 按「客服 + 星期」建索引，格子里查一次就够。 */
  const scheduleIndex = useMemo(() => {
    const index = new Map<string, BackendAiSchedule>();
    schedules.forEach((item) => index.set(`${item.agentId}-${item.weekday}`, item));
    return index;
  }, [schedules]);

  /** 今天的服务时段，用于页头提示。 */
  const todayWindow = useMemo(() => {
    const today = new Date().getDay() === 0 ? 7 : new Date().getDay();
    const rows = schedules.filter((item) => item.weekday === today && item.enabled);
    if (rows.length === 0) return null;
    const starts = rows.map((item) => formatClock(item.startTime)).sort();
    const ends = rows.map((item) => formatClock(item.endTime)).sort();
    return { start: starts[0], end: ends[ends.length - 1], agents: new Set(rows.map((item) => item.agentId)).size };
  }, [schedules]);

  /**
   * 未被权限反查到的历史排班。
   *
   * 账号停用或权限被收回后，名册里查不到这个人，但排班记录还在，而它仍然参与服务时段计算。
   * 不列出来就没法删，所以这里补一行，只展示已存在的班次。
   */
  const extraRows = useMemo(() => {
    const known = new Set(agents.map((agent) => agent.agentId));
    const extras = new Map<number, string>();
    schedules.forEach((item) => {
      if (!known.has(item.agentId)) {
        extras.set(item.agentId, item.agentName || `账号 ${item.agentId}`);
      }
    });
    return Array.from(extras.entries()).map(([agentId, agentName]) => ({ agentId, agentName }));
  }, [agents, schedules]);

  const openCell = (agentId: number, agentName: string, weekday: number) => {
    const existing = scheduleIndex.get(`${agentId}-${weekday}`);
    setEditing({
      id: existing?.id,
      agentId,
      agentName,
      weekday,
      startTime: existing ? formatClock(existing.startTime) : DEFAULT_START,
      endTime: existing ? formatClock(existing.endTime) : DEFAULT_END,
      enabled: existing ? existing.enabled : true,
    });
  };

  const save = async () => {
    if (!editing) return;
    if (!editing.startTime || !editing.endTime) {
      showToast('请填写开始与结束时间', 'warning');
      return;
    }
    if (editing.startTime >= editing.endTime) {
      showToast('结束时间要晚于开始时间', 'warning');
      return;
    }
    setSaving(true);
    try {
      await saveAiAgentSchedule({
        agentId: editing.agentId,
        weekday: editing.weekday,
        startTime: editing.startTime,
        endTime: editing.endTime,
        enabled: editing.enabled,
      });
      showToast(`${editing.agentName} ${WEEKDAYS[editing.weekday - 1]}的班次已保存`, 'success');
      setEditing(null);
      await load();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '保存失败', 'error');
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!editing?.id) return;
    if (!await confirmDialog(`确定删除 ${editing.agentName} ${WEEKDAYS[editing.weekday - 1]}的班次吗？`, '删除排班')) return;
    setSaving(true);
    try {
      await deleteAiAgentSchedule(editing.id);
      showToast('班次已删除', 'success');
      setEditing(null);
      await load();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '删除失败', 'error');
    } finally {
      setSaving(false);
    }
  };

  /** 表单转成接口参数：留空的数字项不传，交给后端按缺省处理。 */
  const toPlanRequest = (form: PlanForm): AiSchedulePlanRequest => ({
    startTime: form.startTime,
    endTime: form.endTime,
    perDay: Number(form.perDay) || 1,
    weekdays: [...form.weekdays].sort((a, b) => a - b),
    maxShiftHours: form.maxShiftHours ? Number(form.maxShiftHours) : undefined,
    maxShiftsPerAgent: form.maxShiftsPerAgent ? Number(form.maxShiftsPerAgent) : undefined,
    clearUncovered: form.clearUncovered,
  });

  /** 用一句话填参数。填完只是把表单改了，排不排还等人点生成预览。 */
  const runParse = async () => {
    if (!planText.trim()) {
      setPlanError('请先写一句排班要求');
      return;
    }
    setParsing(true);
    setPlanError(null);
    try {
      const parsed = await parseAiAgentSchedulePlan(planText.trim());
      setPlanForm({
        startTime: formatClock(parsed.startTime) || DEFAULT_START,
        endTime: formatClock(parsed.endTime) || DEFAULT_END,
        perDay: String(parsed.perDay ?? 1),
        weekdays: parsed.weekdays?.length ? parsed.weekdays : ALL_WEEKDAYS,
        maxShiftHours: parsed.maxShiftHours ? String(parsed.maxShiftHours) : '',
        maxShiftsPerAgent: parsed.maxShiftsPerAgent ? String(parsed.maxShiftsPerAgent) : '',
        clearUncovered: Boolean(parsed.clearUncovered),
      });
      setPlan(null);
      showToast('已按这句话填好参数，确认无误后生成预览', 'success');
    } catch (error) {
      setPlanError(error instanceof Error ? error.message : '没能读懂这句话');
    } finally {
      setParsing(false);
    }
  };

  const runPlan = async () => {
    if (planForm.weekdays.length === 0) {
      setPlanError('至少选一天');
      return;
    }
    if (planForm.startTime >= planForm.endTime) {
      setPlanError('结束时间要晚于开始时间');
      return;
    }
    setPlanning(true);
    setPlanError(null);
    try {
      setPlan(await planAiAgentSchedule(toPlanRequest(planForm)));
    } catch (error) {
      setPlan(null);
      setPlanError(error instanceof Error ? error.message : '生成计划失败');
    } finally {
      setPlanning(false);
    }
  };

  const runApply = async () => {
    setApplying(true);
    setPlanError(null);
    try {
      const applied = await applyAiAgentSchedulePlan(toPlanRequest(planForm));
      showToast(`已应用排班，变动 ${applied.items.length} 条`, 'success');
      closePlan();
      await load();
    } catch (error) {
      setPlanError(error instanceof Error ? error.message : '应用排班失败');
    } finally {
      setApplying(false);
    }
  };

  const renderRow = (agentId: number, agentName: string, status?: AiAgentStatus) => {
    const weeklyHours = WEEKDAYS.reduce((total, _, index) => {
      const item = scheduleIndex.get(`${agentId}-${index + 1}`);
      if (!item || !item.enabled) return total;
      return total + hoursBetween(formatClock(item.startTime), formatClock(item.endTime));
    }, 0);
    return (
      <tr key={agentId} className="border-t border-[#E2E8F0]">
        <th scope="row" className="sticky left-0 bg-white px-3 py-2.5 text-left align-middle">
          <span className="flex items-center gap-2">
            {status && <span className={`w-2 h-2 rounded-full shrink-0 ${STATUS_DOT[status]}`} aria-hidden="true" />}
            <span className="min-w-0">
              <span className="block text-xs font-semibold text-gray-900 truncate">{agentName}</span>
              <span className="block text-[11px] text-gray-400">
                {status ? STATUS_LABEL[status] : '已无接待权限'} · 本周 {weeklyHours} 小时
              </span>
            </span>
          </span>
        </th>
        {WEEKDAYS.map((_, index) => {
          const weekday = index + 1;
          const item = scheduleIndex.get(`${agentId}-${weekday}`);
          const active = editing && editing.agentId === agentId && editing.weekday === weekday;
          return (
            <td key={weekday} className="px-1.5 py-1.5 align-middle">
              <button
                type="button"
                onClick={() => openCell(agentId, agentName, weekday)}
                className={`w-full rounded-lg border px-2 py-2 text-[11px] leading-tight transition-colors ${
                  active
                    ? 'border-blue-400 bg-blue-50'
                    : item
                      ? item.enabled
                        ? 'border-[#E2E8F0] bg-white text-gray-700 hover:bg-slate-50'
                        : 'border-dashed border-[#E2E8F0] bg-slate-50 text-gray-400 hover:bg-slate-100'
                      : 'border-dashed border-[#E2E8F0] text-gray-300 hover:bg-slate-50 hover:text-gray-500'
                }`}
              >
                {item ? (
                  <>
                    <span className="block font-semibold">{formatClock(item.startTime)}-{formatClock(item.endTime)}</span>
                    {!item.enabled && <span className="block mt-0.5">已停用</span>}
                  </>
                ) : (
                  <span className="block">未排班</span>
                )}
              </button>
            </td>
          );
        })}
      </tr>
    );
  };

  return (
    <div className="space-y-5 animate-in fade-in-50 duration-200">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">客服排班</h2>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            按周维护班次。服务时段外买家会看到「非服务时间」并引导留言；实际能否接待仍取决于客服是否上线。
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setPlanOpen(true)}
            className="h-9 px-4 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5"
          >
            <CalendarPlus className="w-4 h-4" />一键排班
          </button>
          <button
            type="button"
            onClick={() => void load()}
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

      <div className="rounded-xl border border-[#E2E8F0] bg-slate-50 px-4 py-3 text-xs text-gray-600">
        <CalendarClock className="w-4 h-4 inline mr-1.5 text-gray-400" />
        {todayWindow
          ? `今日服务时段 ${todayWindow.start}-${todayWindow.end}，共 ${todayWindow.agents} 位客服排班`
          : '今日无排班，买家会看到「今天暂无客服值班」'}
      </div>

      <div className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs overflow-x-auto">
        <table className="w-full min-w-[880px] border-collapse">
          <caption className="sr-only">客服一周排班表，点击格子可编辑班次</caption>
          <thead>
            <tr className="bg-slate-50">
              <th scope="col" className="sticky left-0 bg-slate-50 px-3 py-2.5 text-left text-[11px] font-semibold text-gray-500 w-[190px]">
                客服
              </th>
              {WEEKDAYS.map((day) => (
                <th key={day} scope="col" className="px-2 py-2.5 text-center text-[11px] font-semibold text-gray-500">
                  {day}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading && agents.length === 0 && extraRows.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-xs text-gray-400" role="status">
                  <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />加载中…
                </td>
              </tr>
            ) : agents.length === 0 && extraRows.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-xs text-gray-400" role="status">
                  还没有可排班的客服。持有「客服工作台」权限的账号会出现在这里。
                </td>
              </tr>
            ) : (
              <>
                {agents.map((agent) => renderRow(agent.agentId, agent.agentName, agent.status))}
                {extraRows.map((row) => renderRow(row.agentId, row.agentName))}
              </>
            )}
          </tbody>
        </table>
      </div>

      {editing && (
        <section className="rounded-xl border border-[#E2E8F0] bg-white shadow-xs p-4">
          <h3 className="text-sm font-semibold text-gray-900">
            {editing.agentName} · {WEEKDAYS[editing.weekday - 1]}
            <span className="ml-2 text-xs font-normal text-gray-400">{editing.id ? '修改班次' : '新增班次'}</span>
          </h3>
          <div className="mt-3 flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1 text-xs text-gray-500">
              开始时间
              <input
                type="time"
                value={editing.startTime}
                onChange={(event) => setEditing({ ...editing, startTime: event.target.value })}
                className="h-9 px-2 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
              />
            </label>
            <label className="flex flex-col gap-1 text-xs text-gray-500">
              结束时间
              <input
                type="time"
                value={editing.endTime}
                onChange={(event) => setEditing({ ...editing, endTime: event.target.value })}
                className="h-9 px-2 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
              />
            </label>
            <label className="flex items-center gap-1.5 text-xs text-gray-600 h-9">
              <input
                type="checkbox"
                checked={editing.enabled}
                onChange={(event) => setEditing({ ...editing, enabled: event.target.checked })}
                className="w-4 h-4 accent-blue-600"
              />
              启用
            </label>
            <div className="flex items-center gap-2 ml-auto">
              {editing.id && (
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => void remove()}
                  className="h-9 px-3 rounded-lg border border-rose-200 text-sm font-semibold text-rose-600 hover:bg-rose-50 inline-flex items-center gap-1.5 disabled:opacity-50"
                >
                  <Trash2 className="w-4 h-4" />删除
                </button>
              )}
              <button
                type="button"
                onClick={() => setEditing(null)}
                className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm font-semibold text-gray-700 hover:bg-gray-50"
              >
                取消
              </button>
              <button
                type="button"
                disabled={saving}
                onClick={() => void save()}
                className="h-9 px-3 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5 disabled:opacity-60"
              >
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}保存
              </button>
            </div>
          </div>
        </section>
      )}

      {planOpen && (
        <div
          className="fixed inset-0 z-[100] flex items-start justify-center overflow-y-auto bg-slate-950/45 p-4 backdrop-blur-[2px]"
          role="presentation"
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label="一键排班"
            className="my-auto w-full max-w-2xl rounded-2xl border border-slate-200 bg-white shadow-2xl"
          >
            <div className="flex items-start gap-3 border-b border-slate-100 px-5 py-4">
              <div className="min-w-0 flex-1">
                <h2 className="text-base font-bold text-slate-900">一键排班</h2>
                <p className="mt-1 text-xs text-slate-500">按参数生成整周班次，生成后先看预览再确认写入。</p>
              </div>
              <button
                type="button"
                onClick={closePlan}
                aria-label="关闭"
                className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-4 px-5 py-4">
              <div className="flex flex-wrap items-center gap-2">
                <input
                  value={planText}
                  onChange={(event) => setPlanText(event.target.value)}
                  placeholder="例如：工作日两人，周末一人，早九晚六"
                  aria-label="用一句话说明怎么排班"
                  className="h-9 min-w-[200px] flex-1 rounded-lg border border-slate-200 px-3 text-sm text-slate-800 outline-none placeholder:text-slate-400 focus:border-blue-500"
                />
                <button
                  type="button"
                  disabled={parsing}
                  onClick={() => void runParse()}
                  className="h-9 px-3 rounded-lg border border-slate-200 text-sm font-semibold text-slate-700 hover:bg-slate-50 inline-flex items-center gap-1.5 disabled:opacity-60"
                >
                  {parsing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
                  按这句话填参数
                </button>
              </div>

              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                <label className="flex flex-col gap-1 text-xs text-slate-500">
                  开始时间
                  <input
                    type="time"
                    value={planForm.startTime}
                    onChange={(event) => setPlanForm({ ...planForm, startTime: event.target.value })}
                    className="h-9 px-2 rounded-lg border border-slate-200 text-sm text-slate-800 outline-none focus:border-blue-500"
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-slate-500">
                  结束时间
                  <input
                    type="time"
                    value={planForm.endTime}
                    onChange={(event) => setPlanForm({ ...planForm, endTime: event.target.value })}
                    className="h-9 px-2 rounded-lg border border-slate-200 text-sm text-slate-800 outline-none focus:border-blue-500"
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-slate-500">
                  每天在岗人数
                  <input
                    type="number"
                    min={1}
                    value={planForm.perDay}
                    onChange={(event) => setPlanForm({ ...planForm, perDay: event.target.value })}
                    className="h-9 px-2 rounded-lg border border-slate-200 text-sm text-slate-800 outline-none focus:border-blue-500"
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-slate-500">
                  单班最多几小时（可留空）
                  <input
                    type="number"
                    min={1}
                    value={planForm.maxShiftHours}
                    onChange={(event) => setPlanForm({ ...planForm, maxShiftHours: event.target.value })}
                    className="h-9 px-2 rounded-lg border border-slate-200 text-sm text-slate-800 outline-none focus:border-blue-500"
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-slate-500">
                  每人每周最多几班（可留空）
                  <input
                    type="number"
                    min={1}
                    value={planForm.maxShiftsPerAgent}
                    onChange={(event) => setPlanForm({ ...planForm, maxShiftsPerAgent: event.target.value })}
                    className="h-9 px-2 rounded-lg border border-slate-200 text-sm text-slate-800 outline-none focus:border-blue-500"
                  />
                </label>
              </div>

              <div>
                <span className="text-xs text-slate-500">生成哪几天</span>
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {WEEKDAYS.map((day, index) => {
                    const weekday = index + 1;
                    const selected = planForm.weekdays.includes(weekday);
                    return (
                      <button
                        key={day}
                        type="button"
                        aria-pressed={selected}
                        onClick={() => setPlanForm({
                          ...planForm,
                          weekdays: selected
                            ? planForm.weekdays.filter((item) => item !== weekday)
                            : [...planForm.weekdays, weekday],
                        })}
                        className={`h-8 px-2.5 rounded-lg border text-xs font-medium transition-colors ${
                          selected
                            ? 'border-blue-500 bg-blue-50 text-blue-700'
                            : 'border-slate-200 text-slate-500 hover:bg-slate-50'
                        }`}
                      >
                        {day}
                      </button>
                    );
                  })}
                </div>
              </div>

              <label className="flex items-center gap-1.5 text-xs text-slate-600">
                <input
                  type="checkbox"
                  checked={planForm.clearUncovered}
                  onChange={(event) => setPlanForm({ ...planForm, clearUncovered: event.target.checked })}
                  className="w-4 h-4 accent-blue-600"
                />
                清掉这几天里没被排到的旧班次
              </label>

              <button
                type="button"
                disabled={planning}
                onClick={() => void runPlan()}
                className="h-9 px-4 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5 disabled:opacity-60"
              >
                {planning ? <Loader2 className="w-4 h-4 animate-spin" /> : <CalendarPlus className="w-4 h-4" />}
                生成预览
              </button>

              {planError && (
                <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700" role="alert">
                  <AlertCircle className="w-4 h-4 inline mr-1.5" />{planError}
                </div>
              )}

              {plan && (
                <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-600">
                    <span>共 {plan.items.length} 条变动</span>
                    <span>{plan.shiftCounts.join('、')}</span>
                  </div>

                  {plan.items.length === 0 ? (
                    <p className="text-xs text-slate-500">按当前参数没有需要变动的班次。</p>
                  ) : (
                    <div className="max-h-64 overflow-y-auto rounded-lg border border-slate-200 bg-white">
                      <table className="w-full border-collapse text-xs">
                        <thead>
                          <tr className="bg-slate-50 text-slate-500">
                            <th scope="col" className="px-2 py-2 text-left font-semibold">星期</th>
                            <th scope="col" className="px-2 py-2 text-left font-semibold">客服</th>
                            <th scope="col" className="px-2 py-2 text-left font-semibold">变动</th>
                            <th scope="col" className="px-2 py-2 text-left font-semibold">班次</th>
                          </tr>
                        </thead>
                        <tbody>
                          {plan.items.map((item) => (
                            <tr key={`${item.agentId}-${item.weekday}-${item.change}`} className="border-t border-slate-100">
                              <td className="px-2 py-1.5 text-slate-600">{WEEKDAYS[item.weekday - 1]}</td>
                              <td className="px-2 py-1.5 text-slate-800">{item.agentName}</td>
                              <td className="px-2 py-1.5">
                                <span className={`font-semibold ${
                                  item.change === 'REMOVE'
                                    ? 'text-rose-600'
                                    : item.change === 'ADD'
                                      ? 'text-emerald-600'
                                      : 'text-blue-600'
                                }`}>
                                  {CHANGE_LABEL[item.change]}
                                </span>
                              </td>
                              <td className="px-2 py-1.5 text-slate-700">
                                {item.change === 'REMOVE'
                                  ? `${formatClock(item.previousStart)}-${formatClock(item.previousEnd)}`
                                  : item.previousStart
                                    ? `${formatClock(item.previousStart)}-${formatClock(item.previousEnd)} → ${formatClock(item.startTime)}-${formatClock(item.endTime)}`
                                    : `${formatClock(item.startTime)}-${formatClock(item.endTime)}`}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}

                  {plan.warnings.length > 0 && (
                    <ul className="space-y-1 text-xs text-amber-700">
                      {plan.warnings.map((warning) => (
                        <li key={warning}>
                          <AlertCircle className="w-3.5 h-3.5 inline mr-1" />{warning}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>

            <div className="flex justify-end gap-2 border-t border-slate-100 px-5 py-4">
              <button
                type="button"
                onClick={closePlan}
                className="h-9 rounded-lg border border-slate-200 px-4 text-sm font-medium text-slate-600 transition hover:bg-slate-50"
              >
                取消
              </button>
              <button
                type="button"
                disabled={!plan || plan.items.length === 0 || applying}
                onClick={() => void runApply()}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-blue-600 px-4 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:opacity-60"
              >
                {applying ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                确认应用
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
