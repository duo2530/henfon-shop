import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';
import {
  AiAgentStatus,
  BackendAiAgentDesk,
  BackendAiAgentEvent,
  BackendAiAgentMessage,
  BackendAiAgentSession,
  BackendAiProductCard,
  BackendAiRating,
  changeAiAgentStatus,
  endAiAgentSession,
  getAiAgentDesk,
  joinAiAgentSession,
  listAiAgentQueue,
  listAiAgentRatings,
  listAiAgentSessions,
  searchAiAgentProducts,
  sendAiAgentHeartbeat,
  sendAiAgentProduct,
  sendAiAgentReply,
  streamAiAgentSession,
} from '../../api/adminApi';
import { PermissionGate } from '../common/PermissionGate';
import { formatDateTime } from '../../utils/datetime';
import {
  AlertCircle,
  CheckCircle2,
  Coffee,
  Headphones,
  Inbox,
  Loader2,
  LogOut,
  Package,
  RefreshCw,
  Search,
  Send,
  Star,
  UserRound,
  Zap,
  X,
} from 'lucide-react';

/** 队列轮询间隔。等待接入的会话要保持可见，但不能把数据库查穿。 */
const QUEUE_POLL_MS = 5000;

/** 坐席心跳间隔。服务端 90 秒判定掉线，这里按 30 秒一次留出两次容错。 */
const HEARTBEAT_MS = 30000;

/** 工作台展示的评价条数。 */
const RATING_PREVIEW = 5;

/** 坐席状态的展示文案与配色。 */
const STATUS_META: Record<AiAgentStatus, { label: string; dot: string; text: string }> = {
  ONLINE: { label: '在线', dot: 'bg-emerald-500', text: 'text-emerald-700' },
  BREAK: { label: '小休', dot: 'bg-amber-500', text: 'text-amber-700' },
  OFFLINE: { label: '离线', dot: 'bg-gray-300', text: 'text-gray-500' },
};

/**
 * 把已等待秒数写成可读时长。
 *
 * @param seconds 秒数
 * @returns 形如「3 分 20 秒」的文本
 */
function formatWaiting(seconds?: number | null): string {
  if (seconds === undefined || seconds === null) return '';
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return rest ? `${minutes} 分 ${rest} 秒` : `${minutes} 分`;
}

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
 * 人工客服工作台。
 *
 * 接入是抢单式的：等待队列对所有在线客服可见，谁先点「接入」谁接待。队列用轮询而不是长连接，
 * 是因为队列本身变化稀疏（几分钟一次），为它每条客服端各开一条长连接不值得；点进某条会话后
 * 才切到 SSE，那条链路对延迟敏感。
 *
 * 消息的唯一事实来源是服务端：订阅时会先收到一条快照，里面是最近 50 条记录。所以刷新页面、
 * 短暂断网都不会丢消息，界面不需要自己维护"我发过什么"。
 *
 * 坐席状态是买家能否转人工的依据：没有客服在线时，买家点「转人工」会被直接拒绝并引导去留言。
 * 所以这个页面打开期间要持续心跳，掉线判定交给服务端，界面只负责如实展示。
 *
 * @author Henfon
 * @date 2026-09-21
 */
export const AiAgentWorkbenchView: React.FC = () => {
  const { showToast } = useAdmin();

  const [queue, setQueue] = useState<BackendAiAgentSession[]>([]);
  const [sessions, setSessions] = useState<BackendAiAgentSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [desk, setDesk] = useState<BackendAiAgentDesk | null>(null);
  const [switching, setSwitching] = useState<AiAgentStatus | null>(null);
  const [ratings, setRatings] = useState<BackendAiRating[]>([]);

  const [activeId, setActiveId] = useState<string | null>(null);
  const [active, setActive] = useState<BackendAiAgentSession | null>(null);
  const [messages, setMessages] = useState<BackendAiAgentMessage[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [streamError, setStreamError] = useState<string | null>(null);

  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [joining, setJoining] = useState(false);

  // 商品推送面板：展开、搜索词、结果与提交中。搜索走服务端，不在前端过滤当前页商品——
  // 客服要找的常常是买家提到的那一件，它多半不在客服当前浏览的列表里。
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerKeyword, setPickerKeyword] = useState('');
  const [pickerResults, setPickerResults] = useState<BackendAiProductCard[]>([]);
  const [pickerSearching, setPickerSearching] = useState(false);
  const [pickerError, setPickerError] = useState<string | null>(null);
  const [pickerSending, setPickerSending] = useState<number | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const deskRestoredRef = useRef(false);
  const pickerInputRef = useRef<HTMLInputElement | null>(null);

  // 商品弹框打开即锁住底层滚动，与 ProductPickerModal 一致。
  useBodyScrollLock(pickerOpen);

  const loadLists = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const [nextQueue, nextSessions] = await Promise.all([listAiAgentQueue(), listAiAgentSessions()]);
      setQueue(nextQueue || []);
      setSessions(nextSessions || []);
      setLoadError(null);
    } catch (error) {
      // 轮询失败不清空已有列表：网络抖一下就把队列抹掉，客服会以为没人排队。
      setLoadError(error instanceof Error ? error.message : '队列加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadRatings = useCallback(async () => {
    try {
      setRatings((await listAiAgentRatings(RATING_PREVIEW)) || []);
    } catch {
      // 评价加载失败不影响接待，静默处理，避免一个次要区块的报错盖住队列。
    }
  }, []);

  useEffect(() => {
    void loadLists();
    void loadRatings();
    const timer = window.setInterval(() => void loadLists(true), QUEUE_POLL_MS);
    return () => window.clearInterval(timer);
  }, [loadLists, loadRatings]);

  // 打开页面时读一次坐席状态。上次是在线（哪怕心跳已过期）就立刻续一次心跳：客服正坐在
  // 这个页面前面，不该因为上次关页面时的残留状态被买家当成不在线。
  useEffect(() => {
    void (async () => {
      try {
        const next = await getAiAgentDesk();
        setDesk(next);
        if (next.status !== 'OFFLINE' && !deskRestoredRef.current) {
          deskRestoredRef.current = true;
          setDesk(await sendAiAgentHeartbeat());
        }
      } catch {
        // 状态读不出来不影响接待列表，保持状态卡为空由用户自己点上线。
      }
    })();
  }, []);

  // 在线与小休都要续心跳：小休的客服还坐在工位上，下一秒就可能回来接单，
  // 把他判成掉线会让买家看到的在线人数忽多忽少。
  useEffect(() => {
    if (desk?.status !== 'ONLINE' && desk?.status !== 'BREAK') return;
    const timer = window.setInterval(() => {
      void sendAiAgentHeartbeat().then(setDesk).catch(() => undefined);
    }, HEARTBEAT_MS);
    return () => window.clearInterval(timer);
  }, [desk?.status]);

  // 切换会话或卸载时断开旧的长连接，避免上一个会话的消息推进当前面板。
  useEffect(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setMessages([]);
    setStreamError(null);
    if (!activeId) return;
    // 等待中的会话在 queue 里、已接入的在 sessions 里，两处都要找；找不到也不能把刚选中的
    // 会话清成 null——openSession 已经把它设进 active，轮询列表只是补充来源。
    const target = sessions.find((item) => item.conversationId === activeId)
      || queue.find((item) => item.conversationId === activeId);
    setActive((prev) => target || (prev && prev.conversationId === activeId ? prev : null));
    // 判断能不能订阅要用"最新已知"的接待模式，两个来源任一即可：列表是轮询来的，比状态新；
    // 刚点完「接入」时列表还没刷回来（会话已被摘出队列、sessions 尚未包含它），这时列表查不到，
    // 只能靠 join 写进 active 的 HUMAN。少了这个回退，接入后不会建立长连接，客服看到的是
    // 空消息面板——买家说的话一条都收不到，而自己被拒绝得毫无提示。
    const mode = target ? target.serviceMode : active?.serviceMode;
    if (!activeId || mode !== 'HUMAN') return;

    const controller = new AbortController();
    abortRef.current = controller;
    setStreaming(true);
    void streamAiAgentSession({
      conversationId: activeId,
      signal: controller.signal,
      onEvent: (event: BackendAiAgentEvent) => {
        if (event.type === 'snapshot') {
          setMessages(event.messages || []);
          setActive((prev) => (prev ? { ...prev, serviceMode: (event.serviceMode as BackendAiAgentSession['serviceMode']) || prev.serviceMode } : prev));
          return;
        }
        if (event.type === 'message' && event.message) {
          setMessages((prev) => (
            prev.some((item) => item.sequence === event.message!.sequence)
              ? prev
              : [...prev, event.message!].sort((a, b) => a.sequence - b.sequence)
          ));
          return;
        }
        // 客服推的商品卡片与普通回复是同一条消息，只是带卡片；按同样的方式并入列表，
        // 卡片由服务端装配（图片地址已换发），前端不重新拼。
        if (event.type === 'product' && event.message) {
          setMessages((prev) => (
            prev.some((item) => item.sequence === event.message!.sequence)
              ? prev
              : [...prev, event.message!].sort((a, b) => a.sequence - b.sequence)
          ));
          return;
        }
        if (event.type === 'state') {
          const mode = event.serviceMode as BackendAiAgentSession['serviceMode'] | undefined;
          if (mode) {
            setActive((prev) => (prev ? { ...prev, serviceMode: mode } : prev));
            setQueue((prev) => prev.filter((item) => item.conversationId !== event.conversationId || mode === 'WAITING'));
            void loadLists(true);
          }
          return;
        }
        // 长时间没消息时的催办：只提醒，不动状态。买家可能在慢慢打字，系统不该在这时抢走会话。
        if (event.type === 'idle') {
          showToast(`这个会话已经 ${event.idleMinutes ?? 0} 分钟没有新消息，处理完请点「结束服务」`, 'info');
          return;
        }
        // 会话被别人结束、或被系统的空闲回收收走：面板必须跟着收尾，
        // 否则客服会对着一个已经结束的会话继续打字，发出去的消息买家永远收不到。
        if (event.type === 'ended') {
          showToast('这次人工接待已结束，买家那边已回到智能客服', 'info');
          setActive(null);
          setActiveId(null);
          void loadLists(true);
        }
      },
      onError: (error) => {
        setStreamError(error instanceof Error ? error.message : '消息流已断开');
      },
    }).finally(() => setStreaming(false));
    return () => controller.abort();
    // 只在切换会话、或同一会话从 WAITING 变 HUMAN（刚接入）时重连长连接。
    // sessions 的轮询结果不参与依赖，否则每 5 秒就把长连接重开一次。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId, active?.serviceMode]);

  useEffect(() => {
    const container = scrollRef.current;
    if (container) container.scrollTop = container.scrollHeight;
  }, [messages]);

  // 弹框打开后把光标落进搜索框，客服点开就能直接打字。
  useEffect(() => {
    if (pickerOpen) pickerInputRef.current?.focus();
  }, [pickerOpen]);

  // Esc 关闭弹框。监听挂在捕获阶段：弹框里有输入框，冒泡阶段可能被内部的按键处理吃掉。
  useEffect(() => {
    if (!pickerOpen) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        closePicker();
      }
    };
    document.addEventListener('keydown', onKeyDown, true);
    return () => document.removeEventListener('keydown', onKeyDown, true);
    // closePicker 是组件内稳定的一次性闭包，只依赖面板开关即可。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pickerOpen]);

  const openSession = (session: BackendAiAgentSession) => {
    setActiveId(session.conversationId);
    setActive(session);
    // 上一个会话搜出来的商品留在面板里，很容易误发给下一个买家。
    setPickerOpen(false);
    setPickerKeyword('');
    setPickerResults([]);
    setPickerError(null);
  };

  const switchDeskStatus = async (status: AiAgentStatus) => {
    if (switching) return;
    setSwitching(status);
    try {
      const next = await changeAiAgentStatus(status);
      setDesk(next);
      deskRestoredRef.current = true;
      showToast(status === 'OFFLINE' ? '已下线，买家将看不到你可接入' : `已切换为${STATUS_META[status].label}`,
        status === 'OFFLINE' ? 'info' : 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '状态切换失败', 'error');
    } finally {
      setSwitching(null);
    }
  };

  const join = async () => {
    if (!active) return;
    setJoining(true);
    try {
      const joined = await joinAiAgentSession(active.conversationId);
      // 先把这条从等待队列里摘掉：队列是轮询来的旧快照，留着它会让刷新列表把这行仍然
      // 当成 WAITING 重新安回右侧面板。真正的队列内容随后由 loadLists 覆盖。
      setQueue((prev) => prev.filter((item) => item.conversationId !== joined.conversationId));
      setActive(joined);
      setActiveId(joined.conversationId);
      showToast(`已接入 ${joined.memberName || joined.title || joined.conversationId}`, 'success');
      await loadLists(true);
    } catch (error) {
      // 抢单失败是正常结果（另一位客服先点到），如实提示并刷新队列让界面回到真实状态。
      showToast(error instanceof Error ? error.message : '接入失败，请刷新后重试', 'error');
      await loadLists(true);
    } finally {
      setJoining(false);
    }
  };

  const end = async () => {
    if (!active) return;
    try {
      await endAiAgentSession(active.conversationId);
      showToast('已结束人工接待，买家那边会收到评价邀请', 'success');
      setActive(null);
      setActiveId(null);
      await loadLists(true);
      void loadRatings();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '结束会话失败', 'error');
    }
  };

  const send = async () => {
    const text = input.trim();
    if (!active || !text || sending) return;
    setSending(true);
    try {
      const message = await sendAiAgentReply(active.conversationId, text);
      setInput('');
      // 服务端会把同一条消息推回长连接，这里按序号去重即可，不做本地乐观插入：
      // 乐观插入一旦与服务端的落库顺序不一致，界面上的先后就与买家看到的对不上。
      setMessages((prev) => (
        prev.some((item) => item.sequence === message.sequence)
          ? prev
          : [...prev, message].sort((a, b) => a.sequence - b.sequence)
      ));
    } catch (error) {
      showToast(error instanceof Error ? error.message : '发送失败，请重试', 'error');
    } finally {
      setSending(false);
    }
  };

  /**
   * 切换会话时收起商品面板。
   *
   * 上一个会话搜出来的商品放在这里很容易被误发给下一个买家，收起比留着更安全。
   */
  const closePicker = () => {
    setPickerOpen(false);
    setPickerKeyword('');
    setPickerResults([]);
    setPickerError(null);
  };

  const searchProducts = async () => {
    const keyword = pickerKeyword.trim();
    if (!keyword || pickerSearching) return;
    setPickerSearching(true);
    setPickerError(null);
    try {
      setPickerResults(await searchAiAgentProducts(keyword));
    } catch (error) {
      setPickerResults([]);
      setPickerError(error instanceof Error ? error.message : '搜索失败，请重试');
    } finally {
      setPickerSearching(false);
    }
  };

  const pushProduct = async (card: BackendAiProductCard) => {
    if (!active || pickerSending !== null) return;
    setPickerSending(card.productId);
    try {
      const message = await sendAiAgentProduct(active.conversationId, card.productId);
      // 与普通回复一样只做去重不乐观插入：长连接会把同一条推回来，两边都按序号对齐。
      setMessages((prev) => (
        prev.some((item) => item.sequence === message.sequence)
          ? prev
          : [...prev, message].sort((a, b) => a.sequence - b.sequence)
      ));
      closePicker();
      showToast('商品卡片已发送', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '发送失败，请重试', 'error');
    } finally {
      setPickerSending(null);
    }
  };

  const waitingCount = queue.length;
  const activeIsWaiting = active?.serviceMode === 'WAITING';  const status = desk?.status || 'OFFLINE';
  const statusMeta = STATUS_META[status];

  const sessionsSorted = useMemo(
    () => [...sessions].sort((a, b) => (b.lastMessageAt || '').localeCompare(a.lastMessageAt || '')),
    [sessions],
  );

  return (
    // 高度约束只在 lg 以上生效：桌面端要让消息列表自己滚、输入框钉在视口内；
    // 小屏堆成单列，用文档流自然滚动更合适。减去的是顶栏 56px 加 main 的上下内边距。
    <div className="flex flex-col gap-5 animate-in fade-in-50 duration-200 lg:h-[calc(100vh-56px-4rem)]">
      <div className="flex flex-wrap items-center justify-between gap-3 shrink-0">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">客服工作台</h2>
            {waitingCount > 0 && (
              <span className="text-xs bg-amber-50 text-amber-700 font-semibold px-2 py-0.5 rounded-full border border-amber-200">
                {waitingCount} 人在等待
              </span>
            )}
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            买家点「转人工」后会进入等待队列，接入后即可实时对话；结束接待后买家会收到评价邀请。
          </p>
        </div>
        <button
          type="button"
          onClick={() => void loadLists()}
          className="h-9 px-3 rounded-lg border border-[#E2E8F0] bg-white text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />刷新队列
        </button>
      </div>

      {loadError && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 shrink-0" role="alert">
          <AlertCircle className="w-4 h-4 inline mr-1.5" />{loadError}
        </div>
      )}

      {/* min-h-0 不能省：grid/flex 子项的默认最小高度是内容高度，不给它，下面那些
          overflow 容器就压不下去，整页会被内容撑长、输入框被推到视口之外。 */}
      <div className="grid grid-cols-1 lg:grid-cols-[320px_1fr] gap-5 lg:flex-1 lg:min-h-0">
        <div className="space-y-5 lg:overflow-y-auto lg:pr-1 lg:min-h-0">
          <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-4">
            {/* 坐席名与三个状态按钮分两行：320px 的左栏放不下并排的两组内容，
                挤在一行的结果是名字被截成「客…」，看不出这是谁的状态卡。 */}
            <div className="flex items-center gap-2 min-w-0">
              <span className={`w-2 h-2 rounded-full shrink-0 ${statusMeta.dot}`} aria-hidden="true" />
              <span className="text-sm font-semibold text-gray-900 truncate">
                {desk?.agentName || '我的接待状态'}
              </span>
              <span className={`text-xs shrink-0 ${statusMeta.text}`}>{statusMeta.label}</span>
            </div>
            <div className="mt-2.5 flex items-center gap-1">
              <button
                type="button"
                disabled={switching !== null}
                onClick={() => void switchDeskStatus('ONLINE')}
                className={`h-7 px-2 rounded-md text-[11px] font-semibold inline-flex items-center gap-1 disabled:opacity-50 ${
                  status === 'ONLINE' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'border border-[#E2E8F0] text-gray-600 hover:bg-gray-50'
                }`}
              >
                {switching === 'ONLINE' ? <Loader2 className="w-3 h-3 animate-spin" /> : <Zap className="w-3 h-3" />}上线
              </button>
              <button
                type="button"
                disabled={switching !== null}
                onClick={() => void switchDeskStatus('BREAK')}
                className={`h-7 px-2 rounded-md text-[11px] font-semibold inline-flex items-center gap-1 disabled:opacity-50 ${
                  status === 'BREAK' ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'border border-[#E2E8F0] text-gray-600 hover:bg-gray-50'
                }`}
              >
                {switching === 'BREAK' ? <Loader2 className="w-3 h-3 animate-spin" /> : <Coffee className="w-3 h-3" />}小休
              </button>
              <button
                type="button"
                disabled={switching !== null}
                onClick={() => void switchDeskStatus('OFFLINE')}
                className={`h-7 px-2 rounded-md text-[11px] font-semibold inline-flex items-center gap-1 disabled:opacity-50 ${
                  status === 'OFFLINE' ? 'bg-gray-100 text-gray-700 border border-gray-200' : 'border border-[#E2E8F0] text-gray-600 hover:bg-gray-50'
                }`}
              >
                <LogOut className="w-3 h-3" />离线
              </button>
            </div>

            {desk?.heartbeatExpired && status !== 'OFFLINE' && (
              <p className="mt-2 text-[11px] text-amber-700 bg-amber-50 border border-amber-100 rounded-md px-2 py-1">
                心跳已中断，买家那边暂时看不到你在线，点一次「上线」即可恢复。
              </p>
            )}

            {/* 这两个数字取自己经轮询过的队列与会话列表，而不是状态卡里的快照：
                状态卡只在心跳（30 秒）与切换状态时更新，而列表每 15 秒刷一次，用卡片里的
                数字会让同一屏出现「等待接入 0」压着「1 人在等待」这种自相矛盾的画面。 */}
            <dl className="mt-3 grid grid-cols-3 gap-2 text-center">
              <div className="rounded-lg bg-slate-50 py-2">
                <dt className="text-[11px] text-gray-500">接待中</dt>
                <dd className="text-sm font-semibold text-gray-900">{sessions.length}</dd>
              </div>
              <div className="rounded-lg bg-slate-50 py-2">
                <dt className="text-[11px] text-gray-500">等待接入</dt>
                <dd className="text-sm font-semibold text-gray-900">{waitingCount}</dd>
              </div>
              <div className="rounded-lg bg-slate-50 py-2">
                <dt className="text-[11px] text-gray-500">我的评分</dt>
                <dd className="text-sm font-semibold text-gray-900">
                  {desk?.ratingAverage != null ? desk.ratingAverage.toFixed(1) : '—'}
                  {desk?.ratingCount ? <span className="text-[11px] text-gray-400 font-normal"> ({desk.ratingCount})</span> : null}
                </dd>
              </div>
            </dl>

            <p className="mt-3 text-[11px] text-gray-500">
              今日班次：
              {desk?.todayShift
                ? `${formatClock(desk.todayShift.startTime)}-${formatClock(desk.todayShift.endTime)}${desk.todayShift.enabled ? '' : '（已停用）'}`
                : '未排班'}
            </p>
          </section>

          <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-4">
            <h3 className="text-sm font-semibold text-gray-900 mb-3 flex items-center gap-2">
              <Inbox className="w-4 h-4 text-amber-600" />等待接入
              <span className="text-xs font-normal text-gray-400">{queue.length}</span>
            </h3>
            {loading && !queue.length ? (
              <p className="py-6 text-center text-xs text-gray-400" role="status">
                <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />加载中…
              </p>
            ) : queue.length === 0 ? (
              <p className="py-6 text-center text-xs text-gray-400" role="status">当前没有人在等待</p>
            ) : (
              <ul className="space-y-2">
                {queue.map((session) => (
                  <li key={session.conversationId}>
                    <button
                      type="button"
                      onClick={() => openSession(session)}
                      className={`w-full text-left px-3 py-2.5 rounded-lg border transition-colors ${
                        activeId === session.conversationId
                          ? 'border-blue-300 bg-blue-50/70'
                          : 'border-[#E2E8F0] hover:bg-slate-50'
                      }`}
                    >
                      {/* 主行放买家名字而不是会话标题：标题取的是买家对智能客服说的第一句话，
                          可能只是「你是」这样的片段，客服靠它认不出是谁在等。 */}
                      <span className="flex items-center justify-between gap-2">
                        <span className="text-xs font-semibold text-gray-900 truncate">
                          {session.memberName || (session.memberId ? `会员 ${session.memberId}` : '未登录访客')}
                        </span>
                        <span className="text-[11px] text-gray-500 shrink-0">
                          已等待 {formatWaiting(session.waitingSeconds) || '-'}
                        </span>
                      </span>
                      <span className="block mt-0.5 text-[11px] text-gray-500 line-clamp-2 break-words">
                        {session.lastMessage || session.title || '未命名会话'}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-4">
            <h3 className="text-sm font-semibold text-gray-900 mb-3 flex items-center gap-2">
              <Headphones className="w-4 h-4 text-blue-600" />我正在接待
              <span className="text-xs font-normal text-gray-400">{sessions.length}</span>
            </h3>
            {sessionsSorted.length === 0 ? (
              <p className="py-6 text-center text-xs text-gray-400" role="status">还没有接入中的会话</p>
            ) : (
              <ul className="space-y-2">
                {sessionsSorted.map((session) => (
                  <li key={session.conversationId}>
                    <button
                      type="button"
                      onClick={() => openSession(session)}
                      className={`w-full text-left px-3 py-2.5 rounded-lg border transition-colors ${
                        activeId === session.conversationId
                          ? 'border-blue-300 bg-blue-50/70'
                          : 'border-[#E2E8F0] hover:bg-slate-50'
                      }`}
                    >
                      <span className="block text-xs font-semibold text-gray-900 truncate">
                        {session.memberName || session.title || '未命名会话'}
                      </span>
                      <span className="block mt-0.5 text-[11px] text-gray-500">
                        最近消息 {formatDateTime(session.lastMessageAt, '-')} · {session.messageCount ?? 0} 条
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-4">
            <h3 className="text-sm font-semibold text-gray-900 mb-3 flex items-center gap-2">
              <Star className="w-4 h-4 text-amber-500" />最近评价
            </h3>
            {ratings.length === 0 ? (
              <p className="py-4 text-center text-xs text-gray-400" role="status">还没有收到评价</p>
            ) : (
              <ul className="space-y-2.5">
                {ratings.map((rating) => (
                  <li key={rating.conversationId} className="border-b border-gray-50 last:border-0 pb-2 last:pb-0">
                    <div className="flex items-center gap-1.5">
                      <span className="text-[11px] font-semibold text-amber-600">{rating.score} 星</span>
                      <span className="text-[11px] text-gray-400">{formatDateTime(rating.createdAt, '')}</span>
                    </div>
                    {rating.tags.length > 0 && (
                      <p className="mt-1 flex flex-wrap gap-1">
                        {rating.tags.map((tag) => (
                          <span key={tag} className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-gray-600">{tag}</span>
                        ))}
                      </p>
                    )}
                    {rating.comment && (
                      <p className="mt-1 text-[11px] text-gray-600 leading-relaxed line-clamp-2">{rating.comment}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs flex flex-col min-h-[520px] lg:min-h-0 overflow-hidden">
          {!active ? (
            <div className="flex-1 grid place-items-center text-center px-6 py-16">
              <div>
                <Headphones className="w-8 h-8 mx-auto mb-3 text-gray-300" />
                <p className="text-sm text-gray-500">从左侧选择一位等待中的买家，接入后开始对话</p>
              </div>
            </div>
          ) : (
            <>
              <header className="px-4 py-3 border-b border-gray-100 flex flex-wrap items-center justify-between gap-2 shrink-0">
                <div className="min-w-0">
                  <h3 className="text-sm font-semibold text-gray-900 truncate">
                    {active.memberName || active.title || '未命名会话'}
                  </h3>
                  <p className="text-[11px] text-gray-500 mt-0.5 truncate">
                    {active.memberId ? `会员 ${active.memberId} · ` : ''}
                    {active.title && active.memberName ? `最初问的是：${active.title} · ` : ''}
                    会话 {active.conversationId}
                    {streaming ? ' · 消息接收中' : ''}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {activeIsWaiting ? (
                    <PermissionGate
                      permission="ai:agent:serve"
                      fallback={<span className="text-xs text-gray-500">当前账号无接待权限</span>}
                    >
                      <button
                        type="button"
                        disabled={joining}
                        onClick={() => void join()}
                        className="h-8 px-3 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5 disabled:opacity-60"
                      >
                        {joining ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
                        接入会话
                      </button>
                    </PermissionGate>
                  ) : (
                    <button
                      type="button"
                      onClick={() => void end()}
                      className="h-8 px-3 rounded-lg border border-[#E2E8F0] text-xs font-semibold text-gray-700 hover:bg-gray-50"
                    >
                      结束接待
                    </button>
                  )}
                </div>
              </header>

              {streamError && (
                <div className="px-4 py-2 text-xs text-rose-700 bg-rose-50 border-b border-rose-100 shrink-0" role="alert">
                  {streamError}
                </div>
              )}

              <div ref={scrollRef} className="flex-1 min-h-0 overflow-y-auto px-4 py-3 space-y-3 bg-slate-50/60">
                {messages.length === 0 ? (
                  <p className="py-10 text-center text-xs text-gray-400" role="status">暂无消息</p>
                ) : (
                  messages.map((message) => {
                    const mine = message.from === 'MEMBER' || message.from === 'AI';
                    const label = message.from === 'AGENT' ? '客服' : message.from === 'AI' ? '智能客服' : '买家';
                    return (
                      <div key={message.sequence} className={`flex ${mine ? 'justify-start' : 'justify-end'}`}>
                        <div className="max-w-[80%]">
                          <div className={`mb-1 text-[11px] text-gray-400 ${mine ? '' : 'text-right'}`}>
                            {label} · {formatDateTime(message.createdAt, '')}
                          </div>
                          <div
                            className={`px-3 py-2 rounded-xl text-sm leading-relaxed whitespace-pre-wrap break-words ${
                              mine ? 'bg-white text-zinc-800 border border-zinc-200' : 'bg-[#2563EB] text-white'
                            }`}
                          >
                            {message.content}
                          </div>
                          {/* 卡片放在气泡外：气泡的底色与卡片的白底叠在一起会显得脏。 */}
                          {message.cards && message.cards.length > 0 && (
                            <div className="mt-1.5 space-y-1.5">
                              {message.cards.map((card) => (
                                <div
                                  key={card.productId}
                                  className="flex items-center gap-2.5 p-2 bg-white border border-zinc-200 rounded-lg"
                                >
                                  {card.imageUrl ? (
                                    <img
                                      src={card.imageUrl}
                                      alt={card.title ?? ''}
                                      className="w-10 h-10 object-cover rounded bg-zinc-100 shrink-0"
                                      loading="lazy"
                                    />
                                  ) : (
                                    <div className="w-10 h-10 rounded bg-zinc-100 shrink-0" />
                                  )}
                                  <div className="min-w-0 flex-1">
                                    <div className="text-xs text-zinc-800 truncate">{card.title}</div>
                                    {card.price && (
                                      <div className="text-xs font-semibold text-rose-600">¥{card.price}</div>
                                    )}
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

              {activeIsWaiting ? (
                <div className="px-4 py-4 border-t border-gray-100 text-xs text-gray-500 shrink-0">
                  <UserRound className="w-4 h-4 inline mr-1.5" />接入后即可回复买家。买家在等待期间补充的说明会在接入时随聊天记录一并读到。
                </div>
              ) : (
                <div className="border-t border-gray-100 p-3 shrink-0">
                  <div className="flex items-end gap-2">
                    <button
                      type="button"
                      onClick={() => setPickerOpen(true)}
                      aria-label="发送商品"
                      aria-haspopup="dialog"
                      title="发送商品"
                      className="h-9 w-9 rounded-lg border border-[#E2E8F0] text-gray-500 hover:border-gray-400 inline-flex items-center justify-center shrink-0"
                    >
                      <Package className="w-4 h-4" />
                    </button>
                    <textarea
                      value={input}
                      onChange={(event) => setInput(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' && !event.shiftKey) {
                          event.preventDefault();
                          void send();
                        }
                      }}
                      rows={1}
                      maxLength={500}
                      placeholder="回复买家…"
                      aria-label="回复内容"
                      className="flex-1 max-h-28 px-3 py-2 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500 resize-none"
                    />
                    <button
                      type="button"
                      disabled={sending || !input.trim()}
                      onClick={() => void send()}
                      className="h-9 px-3 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {sending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                      发送
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </section>

        {/* 商品弹框挂在卡片层：作为覆盖层压在会话之上，聊天记录与输入框都保持原样，
            发完卡片关掉弹框就能接着说下一句。 */}
        {pickerOpen && active && (
          <div
            className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-[2px]"
            role="presentation"
          >
            <div
              role="dialog"
              aria-modal="true"
              aria-label="推送商品"
              className="flex max-h-[85vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl animate-in fade-in zoom-in-95 duration-200"
            >
              <div className="flex items-start gap-3 border-b border-slate-100 px-5 py-4 shrink-0">
                <div className="min-w-0 flex-1">
                  <h2 className="text-base font-bold text-slate-900">推送商品</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    选中后买家会在对话里收到带图卡片，点开即是商品详情页。
                  </p>
                </div>
                <button
                  type="button"
                  onClick={closePicker}
                  aria-label="关闭商品选择"
                  className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              <div className="flex items-center gap-2 border-b border-slate-100 px-5 py-3 shrink-0">
                <input
                  ref={pickerInputRef}
                  type="text"
                  value={pickerKeyword}
                  onChange={(event) => setPickerKeyword(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault();
                      void searchProducts();
                    }
                  }}
                  maxLength={50}
                  placeholder="搜商品名称，如「机械键盘」"
                  aria-label="搜索要发送的商品"
                  className="h-9 flex-1 rounded-lg border border-slate-200 px-2.5 text-sm text-slate-800 outline-none transition focus:border-blue-500"
                />
                <button
                  type="button"
                  disabled={pickerSearching || !pickerKeyword.trim()}
                  onClick={() => void searchProducts()}
                  className="inline-flex h-9 items-center gap-1 rounded-lg bg-blue-600 px-3 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  {pickerSearching ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Search className="h-3.5 w-3.5" />}
                  搜索
                </button>
              </div>

              <div className="min-h-0 flex-1 overflow-y-auto px-5 py-3">
                {pickerError ? (
                  <p className="py-8 text-center text-sm text-rose-600" role="alert">{pickerError}</p>
                ) : pickerResults.length === 0 ? (
                  <p className="py-8 text-center text-sm text-slate-400" role="status">
                    {pickerSearching ? '搜索中…' : '输入关键词搜索在售商品'}
                  </p>
                ) : (
                  <ul className="space-y-1.5">
                    {pickerResults.map((card) => (
                      <li key={card.productId}>
                        <button
                          type="button"
                          disabled={pickerSending !== null}
                          onClick={() => void pushProduct(card)}
                          className="flex w-full items-center gap-3 rounded-xl border border-slate-200 p-2.5 text-left transition hover:border-blue-400 disabled:opacity-50"
                        >
                          {card.imageUrl ? (
                            <img
                              src={card.imageUrl}
                              alt={card.title ?? ''}
                              className="h-14 w-14 shrink-0 rounded-lg bg-slate-100 object-cover"
                              loading="lazy"
                            />
                          ) : (
                            <div className="h-14 w-14 shrink-0 rounded-lg bg-slate-100" />
                          )}
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-sm text-slate-800">{card.title}</div>
                            <div className="mt-0.5 flex items-baseline gap-2">
                              {card.price && (
                                <span className="text-sm font-semibold text-rose-600">¥{card.price}</span>
                              )}
                              {card.marketPrice && (
                                <span className="text-xs text-slate-400 line-through">¥{card.marketPrice}</span>
                              )}
                            </div>
                          </div>
                          {pickerSending === card.productId && (
                            <Loader2 className="h-4 w-4 shrink-0 animate-spin text-slate-400" />
                          )}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-5 py-3 text-xs text-slate-400 shrink-0">
                <span>发送给 {active.memberName || active.title || '当前买家'}</span>
                <button
                  type="button"
                  onClick={closePicker}
                  className="h-8 rounded-lg border border-slate-200 px-3 text-xs font-medium text-slate-600 transition hover:bg-slate-50"
                >
                  关闭
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
