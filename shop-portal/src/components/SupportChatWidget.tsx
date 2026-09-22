import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  Check,
  Headphones,
  Link2,
  Loader2,
  MessageSquare,
  Send,
  Star,
  UserRound,
  X,
} from 'lucide-react';
import {
  endPortalAiAgentSession,
  fetchPortalAiAgentAvailability,
  fetchPortalAiAgentState,
  fetchPortalAiConversationMessages,
  fetchPortalAiPendingRating,
  fetchPortalAiStatus,
  hasPortalMemberSession,
  requestPortalAiAgent,
  sendPortalAiAgentMessage,
  streamPortalAiAgent,
  streamPortalAiChat,
  submitPortalAiRating,
  submitPortalAiTicket,
  type PortalAiAgentAvailability,
  type PortalAiProductCard,
} from '../api/portalApi';

/** 发送方：自己 / 智能客服 / 人工客服。气泡样式与标注按它区分。 */
type Speaker = 'MEMBER' | 'AI' | 'AGENT';

/**
 * 会话标识落本地。
 *
 * 人工会话的关键状态在服务端，但「我这条对话是哪个会话」只存在组件内存里。买家在排队时
 * 刷新一下页面就再也接不回来——客服接入后对着空会话说话，买家那边一片安静。所以把它留存。
 * 只存标识，不存消息：消息的事实来源始终是服务端。
 */
const SUPPORT_CONVERSATION_KEY = 'henfon_shop_support_conversation';

/** 排队中刷新可用性的间隔。排队人数变化不快，问得太勤只会白跑数据库。 */
const QUEUE_POLL_INTERVAL_MS = 20000;

/** 正面与负面标签分开给：评分选完之后才出现，避免买家先被一堆标签带着走。 */
const POSITIVE_TAGS = ['响应很快', '态度很好', '解答专业', '问题解决了'];
const NEGATIVE_TAGS = ['等待太久', '答非所问', '态度一般', '问题没解决'];

/** 单次评价最多勾几个标签，须与后端取值一致。 */
const MAX_RATING_TAGS = 3;

interface ChatMessage {
  id: string;
  from: Speaker;
  content: string;
  /**
   * 服务端消息的会话内序号。
   *
   * 人工会话里它是去重与排序的唯一依据；本地气泡（智能客服一轮提问与回答）没有序号，
   * 它们只属于当前这一轮，不参与人工消息的先后判断。
   */
  sequence?: number;
  /** 随消息下发的商品卡片，智能客服检索到、或人工客服推送时携带。 */
  cards?: PortalAiProductCard[];
  /** 助手消息是否仍在生成。 */
  pending?: boolean;
  /** 失败提示，非空时以警示样式替代正文。 */
  error?: string;
}

/** 接待模式，与后端 serviceMode 对齐。 */
type ServiceMode = 'AI' | 'WAITING' | 'HUMAN';

/**
 * 按服务端序号排序。
 *
 * 没有序号的是本地气泡（智能客服那一轮的提问与回答），它们永远是最新的一轮，排在最后。
 *
 * @param a 消息
 * @param b 消息
 * @returns 排序结果
 */
function bySequence(a: ChatMessage, b: ChatMessage): number {
  return (a.sequence ?? Number.MAX_SAFE_INTEGER) - (b.sequence ?? Number.MAX_SAFE_INTEGER);
}

/** 待评价的那次人工服务。 */
interface RatingTarget {
  conversationId: string;
  agentName?: string;
  title?: string;
}

/**
 * 商品详情页的站外可打开地址。
 *
 * 门户是 hash 路由，路径部分照抄当前页面，只换 hash：站点挂在子目录下时也站得住，
 * 而复制出去的链接要能在微信里直接打开，必须是带域名的完整地址。
 *
 * @param productId 商品主键
 * @returns 完整地址
 */
function productShareUrl(productId: number): string {
  return `${window.location.origin}${window.location.pathname}#/product/prod-${productId}`;
}

/**
 * 把商品链接写进剪贴板。
 *
 * 剪贴板接口只在安全上下文里给（https 与 localhost）；内网用 http 打开时它是 undefined，
 * 这时退回选中文本再复制的老办法，两者都不行才告诉买家复制失败，不给一个点了没反应的按钮。
 *
 * @param url 待复制的地址
 * @returns 是否复制成功
 */
async function copyText(url: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url);
      return true;
    }
  } catch {
    // 落到下面的老办法，用户拒绝授权也会走到这里
  }
  try {
    const holder = document.createElement('textarea');
    holder.value = url;
    holder.setAttribute('readonly', '');
    holder.style.position = 'fixed';
    holder.style.opacity = '0';
    document.body.appendChild(holder);
    holder.select();
    const done = document.execCommand('copy');
    document.body.removeChild(holder);
    return done;
  } catch {
    return false;
  }
}

/**
 * 客服推荐的商品卡片。
 *
 * 卡片主体的整块可点：买家在聊天窗口里看到的就是他要找的东西，让他再回去搜索一遍等于把推荐
 * 白做。跳转走站内路由，不新开页面——一旦开新标签，买家就离开了这次对话。
 *
 * 复制链接是另一件事：买家要把它转到微信给朋友看，站内跳转解决不了，才需要一条能带走的
 * 绝对地址。它与整卡跳转是两个动作，所以各自一个按钮，不是嵌在一起的一块。
 *
 * @param props 卡片列表与点击回调
 * @returns 卡片区块，没有卡片时返回 null
 */
const ProductCards: React.FC<{ cards?: PortalAiProductCard[]; onOpen?: (productId: number) => void }> = ({
  cards,
  onOpen,
}) => {
  const [copied, setCopied] = useState<{ id: number; ok: boolean } | null>(null);
  const onCopy = async (card: PortalAiProductCard) => {
    const ok = await copyText(productShareUrl(card.productId));
    setCopied({ id: card.productId, ok });
    window.setTimeout(() => setCopied(null), 1600);
  };
  if (!cards || cards.length === 0) {
    return null;
  }
  return (
    <div className="mt-2 space-y-2">
      {cards.map((card) => {
        const done = copied?.id === card.productId;
        return (
          <div
            key={card.productId}
            className="flex items-center gap-1 p-2 bg-white border border-zinc-200 rounded-lg hover:border-zinc-400 transition"
          >
            <button
              type="button"
              onClick={() => onOpen?.(card.productId)}
              className="flex flex-1 min-w-0 items-center gap-3 text-left"
            >
              {card.imageUrl ? (
                <img
                  src={card.imageUrl}
                  alt={card.title ?? ''}
                  className="w-14 h-14 object-cover rounded-md bg-zinc-100 shrink-0"
                  loading="lazy"
                />
              ) : (
                <div className="w-14 h-14 rounded-md bg-zinc-100 shrink-0" />
              )}
              <div className="min-w-0 flex-1">
                <div className="text-sm text-zinc-800 line-clamp-2">{card.title}</div>
                {card.note && <div className="mt-0.5 text-[11px] text-zinc-500 truncate">{card.note}</div>}
                <div className="mt-1 flex items-baseline gap-2">
                  {card.price && <span className="text-sm font-semibold text-rose-600">¥{card.price}</span>}
                  {card.marketPrice && card.marketPrice !== card.price && (
                    <span className="text-xs text-zinc-400 line-through">¥{card.marketPrice}</span>
                  )}
                </div>
              </div>
            </button>
            <button
              type="button"
              onClick={() => void onCopy(card)}
              aria-label={done ? (copied?.ok ? '链接已复制' : '复制失败') : `复制「${card.title ?? '商品'}」的链接`}
              title="复制链接"
              className={`shrink-0 inline-flex items-center gap-1 px-1.5 py-1 rounded text-[11px] ${
                done && copied?.ok ? 'text-emerald-600' : done ? 'text-rose-500' : 'text-zinc-500 hover:text-zinc-800'
              }`}
            >
              {done && copied?.ok ? (
                <Check className="w-3.5 h-3.5" />
              ) : (
                <Link2 className="w-3.5 h-3.5" />
              )}
              {done ? (copied?.ok ? '已复制' : '复制失败') : '复制链接'}
            </button>
          </div>
        );
      })}
    </div>
  );
};

/**
 * 门户悬浮客服窗口。
 *
 * 两条链路共用一个窗口：智能客服（SSE 流式生成）与实时人工客服（SSE 双向长连接）。
 * 四点刻意的设计：
 *
 * 1. 客服未启用的环境不渲染入口。摆一个点开只报错的悬浮按钮比没有入口更糟。
 * 2. 转人工是真正的实时会话，不是留言。已登录的买家点一下就进等待队列，客服接入后双方
 *    在同一窗口里对话；未登录的买家仍走留言工单——访客没有稳定身份，关掉页面客服就找不到
 *    人，实时会话会变成客服对着空气打字。
 * 3. 消息的唯一事实来源是服务端。人工会话订阅时先收一份快照，刷新页面、短暂断网都不会丢
 *    消息；本地不做乐观插入，界面上的先后顺序与买家在服务端看到的完全一致。
 * 4. 入口只放一个图标。窗口关着的时候才需要"有动静"的提示，所以只有新消息到达或正在等待
 *    接入时才让图标闪，平时常亮——一个永远在闪的按钮等于没有提示。
 *
 * @author Henfon
 * @date 2026-09-21
 */
export const SupportChatWidget: React.FC<{ onOpenProduct?: (productId: number) => void }> = ({
  onOpenProduct,
}) => {
  const [available, setAvailable] = useState(false);
  const [open, setOpen] = useState(false);

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>(
    () => window.localStorage.getItem(SUPPORT_CONVERSATION_KEY) || undefined,
  );
  const [lastTurnUnresolved, setLastTurnUnresolved] = useState(false);

  const [mode, setMode] = useState<ServiceMode>('AI');
  const [waitingSince, setWaitingSince] = useState<number | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [streamError, setStreamError] = useState<string | null>(null);
  const [transferring, setTransferring] = useState(false);
  const [endingSession, setEndingSession] = useState(false);
  const [unread, setUnread] = useState(0);
  const [availability, setAvailability] = useState<PortalAiAgentAvailability | null>(null);
  /** 转人工被拒时的提示。与 streamError 分开：一个是"现在接不了"，一个是"连接断了"。 */
  const [notice, setNotice] = useState<string | null>(null);

  const [ratingTarget, setRatingTarget] = useState<RatingTarget | null>(null);
  const [ratingScore, setRatingScore] = useState(0);
  const [ratingTags, setRatingTags] = useState<string[]>([]);
  const [ratingComment, setRatingComment] = useState('');
  const [ratingSubmitting, setRatingSubmitting] = useState(false);
  const [ratingDone, setRatingDone] = useState(false);
  const [ratingError, setRatingError] = useState<string | null>(null);

  const [ticketOpen, setTicketOpen] = useState(false);
  const [ticketContact, setTicketContact] = useState('');
  const [ticketQuestion, setTicketQuestion] = useState('');
  const [ticketSubmitting, setTicketSubmitting] = useState(false);
  const [ticketNo, setTicketNo] = useState<string | null>(null);
  const [ticketError, setTicketError] = useState<string | null>(null);

  const chatAbortRef = useRef<AbortController | null>(null);
  const agentAbortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);
  /** 长连接回调运行在订阅时的闭包里，读不到最新的 open/mode，用 ref 兜。 */
  const openRef = useRef(false);
  const modeRef = useRef<ServiceMode>('AI');
  /** 买家选择"以后再说"的会话，本次会话内不再弹评价。 */
  const dismissedRatingsRef = useRef<Set<string>>(new Set());
  const loggedIn = hasPortalMemberSession();

  useEffect(() => {
    let cancelled = false;
    void fetchPortalAiStatus()
      .then((status) => {
        if (!cancelled) setAvailable(Boolean(status?.enabled));
      })
      .catch(() => {
        // 取不到状态就不挂入口：宁可没有客服按钮，也不要给一个必然失败的入口。
        if (!cancelled) setAvailable(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 会话标识随用随存，刷新后还能回到同一段对话。
  useEffect(() => {
    if (conversationId) window.localStorage.setItem(SUPPORT_CONVERSATION_KEY, conversationId);
  }, [conversationId]);

  // 组件卸载时取消在途请求，避免对已卸载组件写入状态。
  useEffect(() => () => {
    chatAbortRef.current?.abort();
    agentAbortRef.current?.abort();
  }, []);

  useEffect(() => {
    openRef.current = open;
    // 打开窗口即视为已读，红点只在窗口关着的时候积累。
    if (open) setUnread(0);
  }, [open]);

  useEffect(() => {
    modeRef.current = mode;
  }, [mode]);

  useEffect(() => {
    if (!open) return;
    const container = scrollRef.current;
    if (container) container.scrollTop = container.scrollHeight;
  }, [messages, open]);

  // 排队时长按秒刷新。用本地时间戳递推，不再反复问服务端——排队中每秒发一个请求换一个
  // 数字，对后端是无谓的压力。
  useEffect(() => {
    if (waitingSince === null) {
      setElapsed(0);
      return;
    }
    const tick = () => setElapsed(Math.max(0, Math.floor((Date.now() - waitingSince) / 1000)));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [waitingSince]);

  /**
   * 可用性：窗口打开时问一次，排队中每 20 秒刷新。
   *
   * 不在页面加载时就轮询：每个访客都挂一个定时请求，换来的只是"客服在线"这一句大概率用不上
   * 的话。真正需要它的是排队中的买家——他不知道还要等多久。
   */
  useEffect(() => {
    if (!available || !open) return undefined;
    let cancelled = false;
    const load = () => {
      void fetchPortalAiAgentAvailability()
        .then((next) => {
          if (!cancelled) setAvailability(next);
        })
        .catch(() => undefined);
    };
    load();
    const timer = mode === 'WAITING' ? window.setInterval(load, QUEUE_POLL_INTERVAL_MS) : undefined;
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearInterval(timer);
    };
  }, [available, open, mode]);

  /**
   * 补评价。
   *
   * 客服结束会话时买家可能已经关掉页面，只靠结束那一刻弹一次，评价率会低到没有统计意义。
   * 进入人工流程时也不弹——那次服务正在用，突然盖一层评价界面只会打断他。
   */
  useEffect(() => {
    if (!available || !loggedIn) return undefined;
    let cancelled = false;
    void fetchPortalAiPendingRating()
      .then((pending) => {
        if (cancelled || !pending?.conversationId) return;
        if (modeRef.current !== 'AI' || dismissedRatingsRef.current.has(pending.conversationId)) return;
        setRatingTarget({
          conversationId: pending.conversationId,
          agentName: pending.agentName,
          title: pending.title,
        });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [available, loggedIn]);

  const appendDelta = useCallback((messageId: string, delta: string) => {
    setMessages((prev) => prev.map((item) => (
      item.id === messageId ? { ...item, content: item.content + delta } : item
    )));
  }, []);

  const patchMessage = useCallback((messageId: string, patch: Partial<ChatMessage>) => {
    setMessages((prev) => prev.map((item) => (item.id === messageId ? { ...item, ...patch } : item)));
  }, []);

  /**
   * 把后端下发的消息合并进列表，按序号去重，不做乐观插入。
   *
   * 未读计数只在窗口关着、且消息不是自己发的时候累加：开着窗口还标红点，等于让买家自己
   * 跟自己较劲。
   *
   * 已存在的序号不覆盖而直接丢弃：发送响应与长连接推送会送来同一条消息，两边都带卡片时
   * 覆盖一遍没有意义，而只带一半时覆盖反而会把先到的卡片冲掉。
   */
  const mergeServerMessage = useCallback((
    sequence: number,
    from: Speaker,
    content: string,
    cards?: PortalAiProductCard[],
  ) => {
    setMessages((prev) => {
      if (prev.some((item) => item.sequence === sequence)) {
        return prev;
      }
      // 按序号插入而不是直接追加：发送响应与长连接推送是两条路径，谁先到不定，
      // 直接追加会让买家自己那条排在客服回复后面。
      return [...prev, { id: `s-${sequence}`, sequence, from, content, cards }].sort(bySequence);
    });
    if (from === 'AGENT' && !openRef.current) setUnread((prev) => prev + 1);
  }, []);

  /**
   * 把买家刚才那句补发到人工链路。
   *
   * 用于「客户端还停在智能客服、服务端已是人工接待」的交界：那轮提问会被智能客服接口拒绝，
   * 但买家说的话不能就这么丢掉——不发出去，客服接入后看到的是一片空白，买家则以为自己
   * 的话丢了。补发走的是正常的人工消息接口，落库与推送都跟平时一样。
   */
  const deliverToAgent = useCallback(async (targetId: string, text: string): Promise<boolean> => {
    setSending(true);
    try {
      const message = await sendPortalAiAgentMessage(targetId, text);
      mergeServerMessage(message.sequence, message.from, message.content, message.cards);
      return true;
    } catch (error) {
      setStreamError(error instanceof Error ? error.message : '发送失败，请重试');
      return false;
    } finally {
      setSending(false);
    }
  }, [mergeServerMessage]);

  /**
   * 把会话历史读回界面。
   *
   * 人工服务结束后长连接就断了，此后买家能看到的记录只能自己读回来。少了这一步，客服一结束
   * 服务、买家在评价弹层上点一下，窗口里就空空如也 —— 库里明明还留着整段对话。
   *
   * 合并规则：服务端是唯一事实来源，整表替换；只保留「正在生成的那一轮」的本地气泡（提问与
   * 尚未收到回答的那条），否则整表替换会把这一轮冲掉，回答再也接不上。
   */
  const loadHistory = useCallback(async (targetId: string) => {
    try {
      const history = await fetchPortalAiConversationMessages(targetId);
      const server = (history || []).map((item, index) => ({
        id: item.sequence === undefined ? `h-${index}` : `s-${item.sequence}`,
        sequence: item.sequence,
        from: item.sender,
        content: item.content,
        cards: item.cards,
      }));
      setMessages((prev) => {
        const pendingIndex = prev.findIndex((item) => item.pending);
        // 还在生成的那一轮：连同它前面那条买家提问一起保留。
        const inflight = pendingIndex >= 0 ? prev.slice(Math.max(0, pendingIndex - 1)) : [];
        return inflight.length ? [...server, ...inflight].sort(bySequence) : server;
      });
    } catch {
      // 历史读不到不影响继续提问，界面上仍可发起新一轮。
    }
  }, []);

  /** 客服结束接待后把会话交回智能客服：断开人工长连接，把完整记录读回来继续用 AI 那条链路。 */
  const returnToAi = useCallback((targetId: string) => {
    agentAbortRef.current?.abort();
    setWaitingSince(null);
    setMode('AI');
    void loadHistory(targetId);
  }, [loadHistory]);

  /**
   * 打开人工会话的长连接。
   *
   * 订阅时服务端先推一份快照（最近 50 条），所以这里直接用它替换本地列表——人工会话与
   * 智能客服共用同一份聊天记录，替换之后买家看到的仍是完整的一段对话，不会断成两截。
   */
  const startAgentStream = useCallback((targetId: string) => {
    agentAbortRef.current?.abort();
    const controller = new AbortController();
    agentAbortRef.current = controller;
    setStreamError(null);
    void streamPortalAiAgent({
      conversationId: targetId,
      signal: controller.signal,
      onEvent: (event) => {
        if (event.type === 'snapshot') {
          const list = event.messages || [];
          setMessages((prev) => {
            const snapshot = list.map((item) => ({
              id: `s-${item.sequence}`,
              sequence: item.sequence,
              from: item.from,
              content: item.content,
              cards: item.cards,
            }));
            // 快照取的是"订阅那一刻"的库内状态，订阅与送达之间到达的消息不在里面。
            // 整表替换会把这些更新的消息抹掉——买家刚发出去的那条会凭空消失。所以按序号
            // 保留快照之外的更新消息，只有本地气泡（智能客服那一轮）被丢掉，它已经在快照里。
            const newest = snapshot.length ? Math.max(...snapshot.map((item) => item.sequence)) : 0;
            const newer = prev.filter((item) => item.sequence !== undefined && item.sequence > newest);
            return newer.length ? [...snapshot, ...newer] : snapshot;
          });
          if (event.serviceMode) setMode(event.serviceMode as ServiceMode);
          return;
        }
        if (event.type === 'message' && event.message) {
          mergeServerMessage(event.message.sequence, event.message.from, event.message.content,
            event.message.cards);
          return;
        }
        if (event.type === 'product' && event.message) {
          // 客服推商品与普通回复走同一条落库路径，界面上也应当是同一个气泡，只是多几张卡片。
          mergeServerMessage(event.message.sequence, event.message.from, event.message.content,
            event.message.cards);
          return;
        }
        if (event.type === 'state' && event.serviceMode) {
          const next = event.serviceMode as ServiceMode;
          setMode(next);
          if (next === 'HUMAN') setWaitingSince(null);
          if (next === 'AI') returnToAi(targetId);
          return;
        }
        if (event.type === 'ended') {
          // 客服主动收尾：把窗口拉起来弹评价。买家点了关闭也会再看到一次，这正是要的效果。
          returnToAi(targetId);
          setRatingTarget({ conversationId: targetId });
          setOpen(true);
          // 再问一次拿到客服名称与会话标题，评价界面才能说清"评的是哪一次服务"。
          void fetchPortalAiPendingRating()
            .then((pending) => {
              if (pending?.conversationId !== targetId) return;
              setRatingTarget({
                conversationId: targetId,
                agentName: pending.agentName,
                title: pending.title,
              });
            })
            .catch(() => undefined);
        }
      },
      onError: (error) => {
        setStreamError(error instanceof Error ? error.message : '客服连接已断开，请刷新页面重试');
      },
    });
  }, [mergeServerMessage, returnToAi]);

  /**
   * 刷新后把上一次的会话接回来。
   *
   * 还没结束的人工会话续长连接（快照会把记录带回来）；已经结束的会话没有连接可依赖，
   * 就自己把历史读回来 —— 否则买家回到页面、在评价弹层上点一下，看到的是一片空白，
   * 而这段对话就在库里躺着。
   *
   * 只对已登录会员生效：访客走的是留言工单，没有会话可以接回来。
   */
  useEffect(() => {
    if (!available || !conversationId || !hasPortalMemberSession()) return undefined;
    let cancelled = false;
    void fetchPortalAiAgentState(conversationId)
      .then((state) => {
        if (cancelled) return;
        const next = (state.serviceMode as ServiceMode) || 'AI';
        const targetId = state.conversationId || conversationId;
        if (next === 'AI') {
          void loadHistory(targetId);
          return;
        }
        setMode(next);
        if (next === 'WAITING') setWaitingSince(Date.now() - (state.waitingSeconds || 0) * 1000);
        setOpen(true);
        startAgentStream(targetId);
      })
      .catch(() => {
        // 状态读不到（会话已结束、被清掉，或接口临时失败）也要把历史显示出来，
        // 否则窗口同样是空的。读失败本身不影响继续提问。
        void loadHistory(conversationId);
      });
    return () => {
      cancelled = true;
    };
    // 只在客服可用后尝试一次；startAgentStream 与 loadHistory 都是稳定的。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [available]);

  /**
   * 进入转人工流程。
   *
   * 已登录：先确认现在确实有人接，再进等待队列并建立实时连接。未登录：退回留言工单，
   * 访客没有稳定身份，客服回复时对方多半已经离开页面——留言本就不需要有人在线，所以
   * 访客这条路径不查可用性。
   */
  const transferToAgent = async () => {
    if (transferring) return;
    if (!loggedIn) {
      setTicketOpen(true);
      setTicketNo(null);
      setNotice(null);
      return;
    }
    setTransferring(true);
    setStreamError(null);
    setNotice(null);
    try {
      // 先问一次能不能接：界面上的提示可能已经过期（客服刚下线），排队中的买家会一直
      // 等下去，不如在入口处拦住并给出明确原因。
      const current = await fetchPortalAiAgentAvailability();
      setAvailability(current);
      if (!current.accepting) {
        setNotice(current.message);
        return;
      }
      const state = await requestPortalAiAgent(conversationId || '');
      setConversationId(state.conversationId);
      setMode((state.serviceMode as ServiceMode) || 'WAITING');
      setWaitingSince(state.serviceMode === 'WAITING' ? Date.now() : null);
      setLastTurnUnresolved(false);
      setTicketOpen(false);
      startAgentStream(state.conversationId);
    } catch (error) {
      // 走到这里多半是"点的一瞬间客服下线了"，服务端给的原因就是给人看的，直接透传。
      setNotice(error instanceof Error ? error.message : '转人工失败，请稍后重试');
    } finally {
      setTransferring(false);
    }
  };

  const ask = async () => {
    const question = input.trim();
    if (!question || sending || mode !== 'AI') return;

    const userMessageId = `u-${Date.now()}`;
    const userMessage: ChatMessage = { id: userMessageId, from: 'MEMBER', content: question };
    const assistantId = `a-${Date.now()}`;
    setMessages((prev) => [...prev, userMessage, { id: assistantId, from: 'AI', content: '', pending: true }]);
    setInput('');
    setSending(true);
    setLastTurnUnresolved(false);
    // 新一轮提问时收起上一轮的转人工表单，避免和新的回答混在一起。
    setTicketOpen(false);
    setTicketNo(null);
    setTicketError(null);
    setNotice(null);
    setTicketQuestion(question);

    const controller = new AbortController();
    chatAbortRef.current = controller;

    try {
      await streamPortalAiChat({
        question,
        conversationId,
        signal: controller.signal,
        onEvent: (event) => {
          if (event.type === 'meta') {
            if (event.conversationId) setConversationId(event.conversationId);
            if (event.hit === false) setLastTurnUnresolved(true);
            return;
          }
          if (event.type === 'delta' && event.content) {
            appendDelta(assistantId, event.content);
            return;
          }
          if (event.type === 'done') {
            // 卡片随 done 下发，而不是塞进 delta 的正文：模型只负责说"我给你找了几款"，
            // 图片、价格与链接由服务端按商品主键组装，避免模型把价格或地址说错。
            patchMessage(assistantId, { pending: false, cards: event.cards });
            return;
          }
          if (event.type === 'error') {
            // 人工接待中再走提问接口会被服务端拒绝。这时买家那句话既没落库也没发给客服，
            // 界面还停在"正在回答"，只把模式改回来是不够的——那句话等于凭空消失。
            if (event.code === 'AI_AGENT_SERVING' && conversationId) {
              // 撤掉这一轮的两个临时气泡：消息会由人工链路重新落库并推回来，本地不做乐观插入，
              // 否则同一句话会以本地与远端两个身份各显示一次。
              setMessages((prev) => prev.filter((item) => item.id !== assistantId && item.id !== userMessageId));
              setMode('HUMAN');
              setWaitingSince(null);
              setLastTurnUnresolved(false);
              startAgentStream(conversationId);
              void deliverToAgent(conversationId, question);
              return;
            }
            // 没有会话标识就无法补发（实际走不到：这个错误只在已有会话上触发）。至少要跟上
            // 服务端的接待模式，否则下一句还会走错链路。
            if (event.code === 'AI_AGENT_SERVING') {
              setMode('HUMAN');
              setWaitingSince(null);
            }
            setLastTurnUnresolved(true);
            patchMessage(assistantId, {
              pending: false,
              error: event.message || '客服暂时不可用，请稍后再试',
            });
          }
        },
      });
    } catch (error) {
      // 主动取消（关闭窗口）不算异常，静默收尾即可。
      if (error instanceof DOMException && error.name === 'AbortError') {
        patchMessage(assistantId, { pending: false });
      } else {
        setLastTurnUnresolved(true);
        patchMessage(assistantId, {
          pending: false,
          error: error instanceof Error ? error.message : '客服暂时不可用，请稍后再试',
        });
      }
    } finally {
      // 一个字都没吐出来的回答（例如配额被拒）不能留一个转不完的"正在回答"。
      setMessages((prev) => prev.map((item) => (
        item.id === assistantId && !item.content && !item.error
          ? { ...item, pending: false, error: '客服暂时没有返回内容，请再说一次' }
          : item
      )));
      setSending(false);
      chatAbortRef.current = null;
    }
  };

  const sendToAgent = async (text: string) => {
    if (!conversationId) return;
    // 服务端会把同一条消息推回长连接，这里按序号去重即可；发送失败时保留输入内容，
    // 买家不必重新敲一遍。
    if (await deliverToAgent(conversationId, text)) setInput('');
  };

  const sendMessage = async () => {
    const text = input.trim();
    if (!text || sending) return;
    if (mode === 'AI') {
      await ask();
      return;
    }
    await sendToAgent(text);
  };

  const submitTicket = async () => {
    const contact = ticketContact.trim();
    const question = ticketQuestion.trim();
    if (!contact || !question || ticketSubmitting) return;
    setTicketSubmitting(true);
    setTicketError(null);
    try {
      const result = await submitPortalAiTicket({ conversationId, contact, question });
      setTicketNo(result?.ticketNo || '已提交');
      setTicketOpen(false);
    } catch (error) {
      setTicketError(error instanceof Error ? error.message : '提交失败，请稍后重试');
    } finally {
      setTicketSubmitting(false);
    }
  };

  const dismissRating = () => {
    if (ratingTarget) dismissedRatingsRef.current.add(ratingTarget.conversationId);
    setRatingTarget(null);
    setRatingScore(0);
    setRatingTags([]);
    setRatingComment('');
    setRatingDone(false);
    setRatingError(null);
  };

  // 评价提交成功后自动收起，买家不必再为一句"谢谢"点一次关闭。
  useEffect(() => {
    if (!ratingDone) return undefined;
    const timer = window.setTimeout(dismissRating, 1800);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ratingDone]);

  const submitRating = async () => {
    if (!ratingTarget || ratingScore < 1 || ratingSubmitting) return;
    setRatingSubmitting(true);
    setRatingError(null);
    try {
      await submitPortalAiRating({
        conversationId: ratingTarget.conversationId,
        score: ratingScore,
        tags: ratingTags,
        comment: ratingComment.trim() || undefined,
      });
      setRatingDone(true);
    } catch (error) {
      setRatingError(error instanceof Error ? error.message : '提交失败，请稍后重试');
    } finally {
      setRatingSubmitting(false);
    }
  };

  /**
   * 买家主动结束本次人工咨询。
   *
   * 客服忘点「结束服务」时会话会一直挂在人工接待上，买家提问被拦住、智能客服也用不了。
   * 服务端也有一道空闲自动回收（默认 30 分钟），但那是兜底，不该让买家干等。
   *
   * 结束后走与客服结束完全相同的收尾：交回智能客服、把记录读回来、恢复成能继续提问的状态。
   */
  const endHumanSession = async () => {
    if (endingSession || !conversationId) return;
    setEndingSession(true);
    setStreamError(null);
    try {
      await endPortalAiAgentSession(conversationId);
      returnToAi(conversationId);
    } catch (error) {
      setStreamError(error instanceof Error ? error.message : '结束咨询失败，请稍后重试');
    } finally {
      setEndingSession(false);
    }
  };

  /** 打开窗口时恢复上一次的会话，避免刷新就得重新描述一遍问题。 */
  const openWidget = async () => {
    setOpen(true);
    if (!loggedIn || !conversationId) return;
    try {
      const state = await fetchPortalAiAgentState(conversationId);
      const next = (state.serviceMode as ServiceMode) || 'AI';
      setMode(next);
      if (next === 'WAITING') setWaitingSince(Date.now() - (state.waitingSeconds || 0) * 1000);
      if (next === 'AI') {
        // 人工服务结束后回到智能客服：没有长连接，记录要自己读回来。
        void loadHistory(state.conversationId || conversationId);
        return;
      }
      startAgentStream(conversationId);
    } catch {
      // 恢复失败不影响继续使用，用户仍可直接提问。
    }
  };

  const toggleRatingTag = (tag: string) => {
    setRatingTags((prev) => {
      if (prev.includes(tag)) return prev.filter((item) => item !== tag);
      // 超上限时挤掉最早选的那个，比默默忽略这次点击更好理解。
      return prev.length >= MAX_RATING_TAGS ? [...prev.slice(1), tag] : [...prev, tag];
    });
  };

  if (!available) return null;

  const inHumanFlow = mode !== 'AI';
  const ratingTagsPool = ratingScore >= 4 ? POSITIVE_TAGS : NEGATIVE_TAGS;
  // 只在窗口关着、又有事发生的时候让图标闪：新消息到了、还在等人接、或者有一次服务没评价。
  // 评价面板铺在窗口内部，窗口关着时它没有渲染出口，不闪一下买家根本不知道该点这里。
  const blink = !open && (unread > 0 || mode === 'WAITING' || ratingTarget !== null);
  /** 排队中的补充说明，用的是服务端那句现成的文案，避免两边各写一遍口径。 */
  const queueHint = mode === 'WAITING' && availability
    ? (availability.reason === 'ONLINE'
      ? availability.message
      : '客服暂时不在线，你的排队位置会保留，客服上线后按顺序接入')
    : null;

  return (
    <>
      {!open && (
        <button
          type="button"
          onClick={() => void openWidget()}
          aria-label={unread > 0 ? `在线客服，${unread} 条未读` : '在线客服'}
          title="在线客服"
          className="fixed bottom-6 right-6 z-40 h-12 w-12 rounded-full bg-zinc-900 text-white shadow-lg hover:bg-zinc-800 inline-flex items-center justify-center"
        >
          <Headphones className={`w-5 h-5${blink ? ' animate-blink' : ''}`} />
          {unread > 0 && (
            <span className="absolute -top-1 -right-1 min-w-[20px] h-5 px-1 rounded-full bg-rose-500 text-[11px] font-semibold text-white inline-flex items-center justify-center">
              {unread > 9 ? '9+' : unread}
            </span>
          )}
        </button>
      )}

      {open && (
        <section
          aria-label="在线客服"
          className="fixed bottom-[88px] right-6 z-40 w-[calc(100vw-3rem)] max-w-[380px] h-[520px] max-h-[calc(100vh-7rem)] bg-white rounded-xl border border-zinc-200 shadow-xl flex flex-col overflow-hidden"
        >
          <header className="flex items-center justify-between gap-2 px-4 py-3 border-b border-zinc-100">
            <div className="flex items-center gap-2 min-w-0">
              <Headphones className="w-4 h-4 text-zinc-700 shrink-0" />
              <span className="text-sm font-semibold text-zinc-900 shrink-0">在线客服</span>
              {mode === 'HUMAN' && (
                <span className="text-[11px] px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 shrink-0">
                  人工
                </span>
              )}
            </div>
            <div className="flex items-center gap-1 shrink-0">
              {!inHumanFlow && (
                <button
                  type="button"
                  disabled={transferring}
                  onClick={() => void transferToAgent()}
                  className="h-7 px-2 rounded text-xs font-semibold text-zinc-600 hover:bg-zinc-50 hover:text-zinc-900 disabled:opacity-50 inline-flex items-center gap-1"
                >
                  {transferring ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <UserRound className="w-3.5 h-3.5" />}
                  {loggedIn ? '转人工' : '留言'}
                </button>
              )}
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="关闭在线客服"
                className="p-1 rounded text-zinc-400 hover:text-zinc-700 hover:bg-zinc-50"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </header>

          {mode === 'WAITING' && (
            <div className="px-4 py-2 text-[11px] text-amber-800 bg-amber-50 border-b border-amber-100">
              <span className="inline-flex items-center gap-1.5">
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                正在为你接入人工客服（已等待 {elapsed} 秒）
              </span>
              {queueHint && <span className="block mt-0.5 text-amber-700">{queueHint}</span>}
              <button
                type="button"
                onClick={() => void endHumanSession()}
                disabled={endingSession}
                className="mt-0.5 h-6 px-2 rounded text-[11px] font-semibold text-amber-800 hover:bg-amber-100 disabled:opacity-50 inline-flex items-center gap-1"
              >
                {endingSession ? <Loader2 className="w-3 h-3 animate-spin" /> : null}
                取消排队
              </button>
            </div>
          )}

          {mode === 'HUMAN' && (
            <div className="px-4 py-2 text-[11px] text-emerald-800 bg-emerald-50 border-b border-emerald-100 flex items-center justify-between gap-2">
              <span>人工客服正在接待，结束服务后可以给这次咨询做个评价</span>
              <button
                type="button"
                onClick={() => void endHumanSession()}
                disabled={endingSession}
                className="shrink-0 h-6 px-2 rounded text-[11px] font-semibold text-emerald-700 hover:bg-emerald-100 disabled:opacity-50 inline-flex items-center gap-1"
              >
                {endingSession ? <Loader2 className="w-3 h-3 animate-spin" /> : null}
                结束咨询
              </button>
            </div>
          )}

          {notice && (
            <div className="px-4 py-2 text-[11px] text-amber-800 bg-amber-50 border-b border-amber-100" role="status">
              <p>{notice}</p>
              {loggedIn && !inHumanFlow && (
                <button
                  type="button"
                  onClick={() => {
                    setTicketOpen(true);
                    setTicketNo(null);
                    setNotice(null);
                  }}
                  className="mt-1.5 h-7 px-2.5 rounded-lg border border-amber-300 font-semibold text-amber-900 hover:bg-amber-100"
                >
                  改成留言
                </button>
              )}
            </div>
          )}

          {streamError && (
            <div className="px-4 py-2 text-[11px] text-rose-700 bg-rose-50 border-b border-rose-100" role="alert">
              {streamError}
            </div>
          )}

          <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3 bg-zinc-50/60">
            {messages.length === 0 && (
              <div className="py-10 text-center text-xs text-zinc-400">
                <MessageSquare className="w-6 h-6 mx-auto mb-2 text-zinc-300" />
                有问题可以直接问
              </div>
            )}

            {messages.map((message) => (
              <div key={message.id} className={`flex ${message.from === 'MEMBER' ? 'justify-end' : 'justify-start'}`}>
                <div className="max-w-[85%]">
                  {message.from === 'AGENT' && (
                    <div className="mb-1 text-[11px] text-emerald-700 font-semibold">人工客服</div>
                  )}
                  <div
                    className={`px-3 py-2 rounded-xl text-sm leading-relaxed whitespace-pre-wrap break-words ${
                      message.from === 'MEMBER'
                        ? 'bg-zinc-900 text-white'
                        : message.error
                          ? 'bg-rose-50 text-rose-700 border border-rose-100'
                          : message.from === 'AGENT'
                            ? 'bg-emerald-50 text-zinc-800 border border-emerald-200'
                            : 'bg-white text-zinc-800 border border-zinc-200'
                    }`}
                  >
                    {message.content}
                    {message.pending && !message.content && (
                      <span className="inline-flex items-center gap-1.5 text-zinc-400">
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />正在回答…
                      </span>
                    )}
                    {/* 失败原因必须落到气泡里：只说"出错"而不说是什么错，等于让访客去猜。 */}
                    {!message.pending && message.error && (
                      <span className="block mt-1 text-xs opacity-80">
                        {message.content ? `回答被中断：${message.error}` : message.error}
                      </span>
                    )}
                  </div>
                  {/* 卡片放在气泡外：气泡有自己的底色，图片与价格跟着底色走会显得脏。 */}
                  <ProductCards cards={message.cards} onOpen={onOpenProduct} />
                </div>
              </div>
            ))}

            {!inHumanFlow && lastTurnUnresolved && !sending && !ticketNo && !ticketOpen && (
              <div className="rounded-xl border border-zinc-200 bg-white px-3 py-3 space-y-2">
                <p className="text-xs text-zinc-600">
                  {loggedIn
                    ? '这个问题客服没能给出确切答复，可以转人工客服实时咨询。'
                    : '这个问题客服没能给出确切答复，可以留个联系方式，让客服跟进。'}
                </p>
                <button
                  type="button"
                  onClick={() => void transferToAgent()}
                  className="h-8 px-3 rounded-lg border border-zinc-300 text-xs font-semibold text-zinc-700 hover:bg-zinc-50"
                >
                  转人工
                </button>
              </div>
            )}

            {!inHumanFlow && ticketOpen && !ticketNo && (
              <div className="rounded-xl border border-zinc-200 bg-white px-3 py-3 space-y-2">
                <p className="text-xs text-zinc-600">留个联系方式，客服上线后会尽快回复你。</p>
                <input
                  type="text"
                  value={ticketContact}
                  onChange={(event) => setTicketContact(event.target.value)}
                  placeholder="手机号或邮箱"
                  aria-label="联系方式"
                  className="w-full h-8 px-2.5 rounded-lg border border-zinc-200 text-xs text-zinc-800 outline-none focus:border-zinc-400"
                />
                <textarea
                  value={ticketQuestion}
                  onChange={(event) => setTicketQuestion(event.target.value)}
                  rows={2}
                  aria-label="要咨询的问题"
                  className="w-full px-2.5 py-1.5 rounded-lg border border-zinc-200 text-xs text-zinc-800 outline-none focus:border-zinc-400 resize-y"
                />
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={ticketSubmitting}
                    onClick={() => void submitTicket()}
                    className="h-8 px-3 rounded-lg bg-zinc-900 text-white text-xs font-semibold hover:bg-zinc-800 inline-flex items-center gap-1.5 disabled:opacity-60"
                  >
                    {ticketSubmitting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
                    提交
                  </button>
                  <button
                    type="button"
                    onClick={() => setTicketOpen(false)}
                    className="h-8 px-2.5 rounded-lg text-xs text-zinc-500 hover:bg-zinc-50 inline-flex items-center gap-1"
                  >
                    <ArrowLeft className="w-3.5 h-3.5" />返回
                  </button>
                </div>
                {ticketError && <p className="text-xs text-rose-600">{ticketError}</p>}
              </div>
            )}

            {ticketNo && (
              <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2.5 text-xs text-emerald-800">
                已提交，工单号 {ticketNo}。登录后在「个人中心 → 我的工单」可以看到客服的回复。
              </div>
            )}
          </div>

          <div className="border-t border-zinc-100 p-3">
            <div className="flex items-end gap-2">
              <textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    void sendMessage();
                  }
                }}
                rows={1}
                maxLength={500}
                placeholder={mode === 'WAITING' ? '客服接入前也可以先把问题说清楚…' : '描述你遇到的问题…'}
                aria-label="提问内容"
                className="flex-1 max-h-24 px-3 py-2 rounded-lg border border-zinc-200 text-sm text-zinc-800 outline-none focus:border-zinc-400 resize-none"
              />
              <button
                type="button"
                disabled={sending || !input.trim()}
                onClick={() => void sendMessage()}
                aria-label="发送"
                className="h-9 px-3 rounded-lg bg-zinc-900 text-white text-sm font-semibold hover:bg-zinc-800 inline-flex items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {sending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {ratingTarget && (
            <div className="absolute inset-0 z-10 bg-white flex flex-col">
              {ratingDone ? (
                <div className="flex-1 flex flex-col items-center justify-center gap-2 px-6 text-center">
                  <Star className="w-8 h-8 text-amber-400" fill="currentColor" />
                  <p className="text-sm font-semibold text-zinc-900">感谢你的评价</p>
                  <p className="text-xs text-zinc-500">你的反馈会帮我们改进服务</p>
                </div>
              ) : (
                <>
                  <header className="flex items-center justify-between px-4 py-3 border-b border-zinc-100">
                    <span className="text-sm font-semibold text-zinc-900">服务评价</span>
                    <button
                      type="button"
                      onClick={dismissRating}
                      aria-label="关闭评价"
                      className="p-1 rounded text-zinc-400 hover:text-zinc-700 hover:bg-zinc-50"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </header>

                  <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
                    <p className="text-xs text-zinc-500">
                      {ratingTarget.agentName
                        ? `${ratingTarget.agentName} 结束了本次服务`
                        : '本次人工服务已结束'}
                      {ratingTarget.title ? ` · ${ratingTarget.title}` : ''}
                    </p>

                    <div>
                      <div className="flex items-center gap-1.5" role="radiogroup" aria-label="满意度评分">
                        {[1, 2, 3, 4, 5].map((value) => (
                          <button
                            key={value}
                            type="button"
                            role="radio"
                            aria-checked={ratingScore === value}
                            aria-label={`${value} 星`}
                            onClick={() => {
                              setRatingScore(value);
                              // 评分变了，上一档的标签就不再适用，直接清掉。
                              setRatingTags([]);
                            }}
                            className="p-1 rounded hover:bg-zinc-50"
                          >
                            <Star
                              className={`w-7 h-7 ${value <= ratingScore ? 'text-amber-400' : 'text-zinc-300'}`}
                              fill={value <= ratingScore ? 'currentColor' : 'none'}
                            />
                          </button>
                        ))}
                      </div>
                      <p className="mt-1 text-[11px] text-zinc-400">
                        {ratingScore === 0
                          ? '点星星打个分'
                          : ratingScore >= 4
                            ? '很满意'
                            : ratingScore >= 3
                              ? '一般'
                              : '不太满意'}
                      </p>
                    </div>

                    {ratingScore > 0 && (
                      <div className="flex flex-wrap gap-1.5">
                        {ratingTagsPool.map((tag) => (
                          <button
                            key={tag}
                            type="button"
                            aria-pressed={ratingTags.includes(tag)}
                            onClick={() => toggleRatingTag(tag)}
                            className={`h-7 px-2.5 rounded-full border text-xs ${
                              ratingTags.includes(tag)
                                ? 'border-zinc-900 bg-zinc-900 text-white'
                                : 'border-zinc-200 text-zinc-600 hover:bg-zinc-50'
                            }`}
                          >
                            {tag}
                          </button>
                        ))}
                      </div>
                    )}

                    <textarea
                      value={ratingComment}
                      onChange={(event) => setRatingComment(event.target.value)}
                      rows={3}
                      maxLength={200}
                      aria-label="评价留言"
                      placeholder="还想说点什么（选填）"
                      className="w-full px-2.5 py-2 rounded-lg border border-zinc-200 text-xs text-zinc-800 outline-none focus:border-zinc-400 resize-y"
                    />

                    {ratingError && <p className="text-xs text-rose-600">{ratingError}</p>}
                  </div>

                  <div className="border-t border-zinc-100 p-3 flex items-center gap-2">
                    <button
                      type="button"
                      disabled={ratingScore < 1 || ratingSubmitting}
                      onClick={() => void submitRating()}
                      className="flex-1 h-9 rounded-lg bg-zinc-900 text-white text-sm font-semibold hover:bg-zinc-800 inline-flex items-center justify-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {ratingSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
                      提交评价
                    </button>
                    <button
                      type="button"
                      onClick={dismissRating}
                      className="h-9 px-3 rounded-lg text-xs text-zinc-500 hover:bg-zinc-50"
                    >
                      以后再说
                    </button>
                  </div>
                </>
              )}
            </div>
          )}
        </section>
      )}
    </>
  );
};