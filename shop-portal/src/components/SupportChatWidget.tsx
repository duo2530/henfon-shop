import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  Headphones,
  Loader2,
  MessageSquare,
  Send,
  X,
} from 'lucide-react';
import {
  fetchPortalAiStatus,
  streamPortalAiChat,
  submitPortalAiTicket,
} from '../api/portalApi';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  /** 助手消息是否仍在生成。 */
  pending?: boolean;
  /** 本轮是否命中了知识库，false 且已结束时给出转人工入口。 */
  hit?: boolean;
  /** 失败提示，非空时以警示样式替代正文。 */
  error?: string;
}

/**
 * 门户悬浮客服窗口。
 *
 * 对访客开放：未登录也能问商品与规则类问题，登录后同一会话会被归属到会员账号上。
 *
 * 三个刻意的处理：
 *
 * 1. 客服未启用的环境不渲染入口。摆一个点开只报错的悬浮按钮比没有入口更糟，所以先用
 *    一次轻量查询确认可用性再决定是否挂出。
 * 2. 关窗口不中断正在生成的回答。后端把「已经说出去的内容」记进会话，前端主动断开会让
 *    运营看到的记录缺一段；组件卸载时才真正取消。
 * 3. 转人工的入口只在两种情况下出现：知识库没命中，或本轮生成失败。答案有资料依据时
 *    不该同时摆一个"要不要转人工"，那会削弱回答本身的可信度。
 *
 * @author Henfon
 * @date 2026-09-21
 */
export const SupportChatWidget: React.FC = () => {
  const [available, setAvailable] = useState(false);
  const [open, setOpen] = useState(false);

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>(undefined);
  const [lastTurnUnresolved, setLastTurnUnresolved] = useState(false);

  const [ticketOpen, setTicketOpen] = useState(false);
  const [ticketContact, setTicketContact] = useState('');
  const [ticketQuestion, setTicketQuestion] = useState('');
  const [ticketSubmitting, setTicketSubmitting] = useState(false);
  const [ticketNo, setTicketNo] = useState<string | null>(null);
  const [ticketError, setTicketError] = useState<string | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

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

  // 组件卸载时取消在途请求，避免对已卸载组件写入状态。
  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    if (!open) return;
    const container = scrollRef.current;
    if (container) container.scrollTop = container.scrollHeight;
  }, [messages, open]);

  const appendDelta = useCallback((messageId: string, delta: string) => {
    setMessages((prev) => prev.map((item) => (
      item.id === messageId ? { ...item, content: item.content + delta } : item
    )));
  }, []);

  const patchMessage = useCallback((messageId: string, patch: Partial<ChatMessage>) => {
    setMessages((prev) => prev.map((item) => (item.id === messageId ? { ...item, ...patch } : item)));
  }, []);

  const ask = async () => {
    const question = input.trim();
    if (!question || sending) return;

    const userMessage: ChatMessage = { id: `u-${Date.now()}`, role: 'user', content: question };
    const assistantId = `a-${Date.now()}`;
    setMessages((prev) => [...prev, userMessage, { id: assistantId, role: 'assistant', content: '', pending: true }]);
    setInput('');
    setSending(true);
    setLastTurnUnresolved(false);
    // 新一轮提问时收起上一轮的转人工表单，避免和新的回答混在一起。
    setTicketOpen(false);
    setTicketNo(null);
    setTicketError(null);
    setTicketQuestion(question);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      await streamPortalAiChat({
        question,
        conversationId,
        signal: controller.signal,
        onEvent: (event) => {
          if (event.type === 'meta') {
            if (event.conversationId) setConversationId(event.conversationId);
            patchMessage(assistantId, { hit: event.hit });
            if (event.hit === false) setLastTurnUnresolved(true);
            return;
          }
          if (event.type === 'delta' && event.content) {
            appendDelta(assistantId, event.content);
            return;
          }
          if (event.type === 'done') {
            patchMessage(assistantId, { pending: false });
            return;
          }
          if (event.type === 'error') {
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
      abortRef.current = null;
    }
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

  if (!available) return null;

  return (
    <>
      {!open && (
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-label="打开在线客服"
          className="fixed bottom-6 right-6 z-40 h-12 px-4 rounded-full bg-zinc-900 text-white shadow-lg hover:bg-zinc-800 inline-flex items-center gap-2 text-sm font-semibold"
        >
          <Headphones className="w-4 h-4" />
          在线客服
        </button>
      )}

      {open && (
        <section
          aria-label="在线客服"
          className="fixed bottom-[88px] right-6 z-40 w-[calc(100vw-3rem)] max-w-[380px] h-[520px] max-h-[calc(100vh-7rem)] bg-white rounded-xl border border-zinc-200 shadow-xl flex flex-col overflow-hidden"
        >
          <header className="flex items-center justify-between px-4 py-3 border-b border-zinc-100">
            <div className="flex items-center gap-2">
              <Headphones className="w-4 h-4 text-zinc-700" />
              <span className="text-sm font-semibold text-zinc-900">在线客服</span>
            </div>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="关闭在线客服"
              className="p-1 rounded text-zinc-400 hover:text-zinc-700 hover:bg-zinc-50"
            >
              <X className="w-4 h-4" />
            </button>
          </header>

          <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3 bg-zinc-50/60">
            {messages.length === 0 && (
              <div className="py-10 text-center text-xs text-zinc-400">
                <MessageSquare className="w-6 h-6 mx-auto mb-2 text-zinc-300" />
                有问题可以直接问
              </div>
            )}

            {messages.map((message) => (
              <div key={message.id} className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div
                  className={`max-w-[85%] px-3 py-2 rounded-xl text-sm leading-relaxed whitespace-pre-wrap break-words ${
                    message.role === 'user'
                      ? 'bg-zinc-900 text-white'
                      : message.error
                        ? 'bg-rose-50 text-rose-700 border border-rose-100'
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
              </div>
            ))}

            {lastTurnUnresolved && !sending && !ticketNo && (
              <div className="rounded-xl border border-zinc-200 bg-white px-3 py-3 space-y-2">
                <p className="text-xs text-zinc-600">这个问题客服没能给出确切答复，可以留个联系方式，让客服跟进。</p>
                {!ticketOpen ? (
                  <button
                    type="button"
                    onClick={() => setTicketOpen(true)}
                    className="h-8 px-3 rounded-lg border border-zinc-300 text-xs font-semibold text-zinc-700 hover:bg-zinc-50"
                  >
                    转人工
                  </button>
                ) : (
                  <div className="space-y-2">
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
              </div>
            )}

            {ticketNo && (
              <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2.5 text-xs text-emerald-800">
                已提交，工单号 {ticketNo}，客服会尽快联系你。
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
                    void ask();
                  }
                }}
                rows={1}
                maxLength={500}
                placeholder="描述你遇到的问题…"
                aria-label="提问内容"
                className="flex-1 max-h-24 px-3 py-2 rounded-lg border border-zinc-200 text-sm text-zinc-800 outline-none focus:border-zinc-400 resize-none"
              />
              <button
                type="button"
                disabled={sending || !input.trim()}
                onClick={() => void ask()}
                aria-label="发送"
                className="h-9 px-3 rounded-lg bg-zinc-900 text-white text-sm font-semibold hover:bg-zinc-800 inline-flex items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {sending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
              </button>
            </div>
          </div>
        </section>
      )}
    </>
  );
};
