import React, { useState, useRef, useEffect } from 'react';
import { Product } from '../types/ecommerce';
import {
  Scale,
  X,
  ArrowRight,
  Trash2,
  ChevronDown,
  ChevronUp,
  RotateCcw,
  History,
  Clock,
  Check,
  Plus,
  Layers,
  Sparkles,
} from 'lucide-react';

export interface CompareHistoryRecord {
  id: string;
  timestamp: number;
  products: Product[];
}

interface CompareFloatingBarProps {
  compareProducts: Product[];
  isOpen: boolean;
  onOpenModal: () => void;
  onRemoveProduct: (productId: string) => void;
  onClearAll: () => void;
  onCloseBar: () => void;
  // History & Restore Props
  lastComparedProducts?: Product[];
  compareHistory?: CompareHistoryRecord[];
  onRestoreLastCompare?: () => void;
  onRestoreFromHistory?: (productIds: string[]) => void;
  onDeleteHistoryItem?: (historyId: string) => void;
  onClearHistory?: () => void;
}

export const CompareFloatingBar: React.FC<CompareFloatingBarProps> = ({
  compareProducts,
  isOpen,
  onOpenModal,
  onRemoveProduct,
  onClearAll,
  onCloseBar,
  lastComparedProducts = [],
  compareHistory = [],
  onRestoreLastCompare,
  onRestoreFromHistory,
  onDeleteHistoryItem,
  onClearHistory,
}) => {
  const [isMinimized, setIsMinimized] = useState(false);
  const [showHistoryPanel, setShowHistoryPanel] = useState(false);
  const [isDismissedPrompt, setIsDismissedPrompt] = useState(false);
  const historyRef = useRef<HTMLDivElement>(null);

  // Close history dropdown on outside click
  useEffect(() => {
    const handleOutsideClick = (e: MouseEvent) => {
      if (historyRef.current && !historyRef.current.contains(e.target as Node)) {
        setShowHistoryPanel(false);
      }
    };
    if (showHistoryPanel) {
      document.addEventListener('mousedown', handleOutsideClick);
    }
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
    };
  }, [showHistoryPanel]);

  // Check if last comparison is different from current comparison
  const isLastDifferentFromCurrent = React.useMemo(() => {
    if (!lastComparedProducts || lastComparedProducts.length === 0) return false;
    if (compareProducts.length !== lastComparedProducts.length) return true;
    const currentIds = new Set(compareProducts.map((p) => p.id));
    return lastComparedProducts.some((p) => !currentIds.has(p.id));
  }, [compareProducts, lastComparedProducts]);

  // Helper to format friendly relative time
  const formatTimeAgo = (timestamp: number): string => {
    const diff = Date.now() - timestamp;
    const seconds = Math.floor(diff / 1000);
    if (seconds < 60) return '刚刚';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}分钟前`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}小时前`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}天前`;
    const date = new Date(timestamp);
    return `${date.getMonth() + 1}月${date.getDate()}日`;
  };

  // 1. If active compare list is empty but we have a recently saved comparison, show the floating restore banner
  if (compareProducts.length === 0) {
    if (
      !isOpen ||
      isDismissedPrompt ||
      !lastComparedProducts ||
      lastComparedProducts.length === 0 ||
      !onRestoreLastCompare
    ) {
      return null;
    }

    return (
      <div className="fixed bottom-5 left-1/2 -translate-x-1/2 z-40 w-[95%] max-w-xl animate-in slide-in-from-bottom-6 duration-300">
        <div className="bg-zinc-900/95 backdrop-blur-md text-white rounded-3xl border border-amber-500/30 shadow-2xl p-3.5 sm:p-4 overflow-hidden relative group">
          {/* Subtle top glow */}
          <div className="absolute top-0 left-1/4 right-1/4 h-px bg-gradient-to-r from-transparent via-amber-400/60 to-transparent" />

          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
            {/* Left info & thumbnails */}
            <div className="flex items-center gap-3 min-w-0 flex-1">
              <div className="w-9 h-9 rounded-xl bg-amber-400/15 text-amber-400 flex items-center justify-center shrink-0 border border-amber-400/20">
                <RotateCcw className="w-4 h-4" />
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-white">发现上次关闭的对比组合</span>
                  <span className="text-[10px] px-1.5 py-0.2 rounded-md bg-amber-400/20 text-amber-300 font-bold border border-amber-400/30">
                    {lastComparedProducts.length} 款商品
                  </span>
                </div>
                <div className="flex items-center gap-1.5 mt-1">
                  <div className="flex -space-x-2 overflow-hidden shrink-0">
                    {lastComparedProducts.map((p) => (
                      <img
                        key={p.id}
                        src={p.images[0]}
                        alt={p.title}
                        className="inline-block w-5 h-5 rounded-md ring-1 ring-zinc-800 object-cover bg-zinc-800"
                        title={p.title}
                      />
                    ))}
                  </div>
                  <p className="text-[11px] text-zinc-400 truncate">
                    {lastComparedProducts.map((p) => p.title).join(' vs ')}
                  </p>
                </div>
              </div>
            </div>

            {/* Right actions */}
            <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
              {compareHistory && compareHistory.length > 1 && (
                <button
                  onClick={() => setShowHistoryPanel(!showHistoryPanel)}
                  className="px-2.5 py-1.5 rounded-xl text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 transition flex items-center gap-1"
                >
                  <History className="w-3.5 h-3.5 text-amber-400" />
                  <span>历史 ({compareHistory.length})</span>
                </button>
              )}

              <button
                onClick={() => {
                  onRestoreLastCompare();
                  setIsDismissedPrompt(false);
                }}
                className="px-3.5 py-1.5 rounded-xl text-xs font-bold bg-amber-400 hover:bg-amber-300 text-zinc-950 shadow-md active:scale-95 transition flex items-center gap-1.5"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>恢复上次对比</span>
              </button>

              <button
                onClick={() => setIsDismissedPrompt(true)}
                className="p-1.5 rounded-xl text-zinc-500 hover:text-zinc-300 hover:bg-zinc-800 transition"
                title="关闭提示"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* History dropdown overlay when in empty-state prompt */}
          {showHistoryPanel && compareHistory.length > 0 && (
            <div
              ref={historyRef}
              className="mt-3 pt-3 border-t border-zinc-800 space-y-2 animate-in fade-in slide-in-from-top-2 duration-200"
            >
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-zinc-400 flex items-center gap-1">
                  <Clock className="w-3 h-3 text-amber-400" />
                  历史对比组合记录
                </span>
                {onClearHistory && (
                  <button
                    onClick={onClearHistory}
                    className="text-[10px] text-zinc-500 hover:text-rose-400 transition"
                  >
                    清空历史
                  </button>
                )}
              </div>

              <div className="max-h-48 overflow-y-auto space-y-1.5 pr-1 custom-scrollbar">
                {compareHistory.map((item) => (
                  <div
                    key={item.id}
                    className="p-2 rounded-xl bg-zinc-800/80 hover:bg-zinc-800 border border-zinc-700/60 flex items-center justify-between gap-2 transition"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="flex -space-x-1.5 overflow-hidden shrink-0">
                        {item.products.map((p) => (
                          <img
                            key={p.id}
                            src={p.images[0]}
                            alt={p.title}
                            className="w-6 h-6 rounded-md ring-1 ring-zinc-800 object-cover bg-zinc-900"
                          />
                        ))}
                      </div>
                      <div className="min-w-0">
                        <p className="text-[11px] font-medium text-zinc-200 truncate">
                          {item.products.map((p) => p.title).join(' / ')}
                        </p>
                        <span className="text-[10px] text-zinc-500">
                          {formatTimeAgo(item.timestamp)} · {item.products.length}款商品
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        onClick={() => {
                          if (onRestoreFromHistory) {
                            onRestoreFromHistory(item.products.map((p) => p.id));
                            setShowHistoryPanel(false);
                          }
                        }}
                        className="px-2 py-1 rounded-lg text-[10px] font-bold bg-amber-400/20 text-amber-300 hover:bg-amber-400 hover:text-zinc-950 transition flex items-center gap-1"
                      >
                        <RotateCcw className="w-2.5 h-2.5" />
                        <span>恢复</span>
                      </button>
                      {onDeleteHistoryItem && (
                        <button
                          onClick={() => onDeleteHistoryItem(item.id)}
                          className="p-1 text-zinc-500 hover:text-rose-400 transition rounded"
                          title="删除记录"
                        >
                          <X className="w-3 h-3" />
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  // 2. If bar is closed by parent
  if (!isOpen) return null;

  return (
    <div className="fixed bottom-5 left-1/2 -translate-x-1/2 z-40 w-[95%] max-w-2xl animate-in slide-in-from-bottom-6 duration-300">
      <div className="bg-zinc-900/95 backdrop-blur-md text-white rounded-3xl border border-zinc-700/80 shadow-2xl overflow-hidden transition-all relative">
        {/* Minimized bar */}
        {isMinimized ? (
          <div className="px-5 py-3 flex items-center justify-between">
            <div
              onClick={() => setIsMinimized(false)}
              className="flex items-center gap-3 cursor-pointer select-none"
            >
              <div className="w-8 h-8 rounded-xl bg-white/10 flex items-center justify-center text-amber-400">
                <Scale className="w-4 h-4" />
              </div>
              <div>
                <span className="text-xs font-bold block">
                  商品对比栏（已选 {compareProducts.length}/3 款）
                </span>
                <span className="text-[10px] text-zinc-400">点击展开详细卡片</span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {isLastDifferentFromCurrent && onRestoreLastCompare && (
                <button
                  onClick={onRestoreLastCompare}
                  className="px-2.5 py-1.5 rounded-xl text-xs font-medium text-amber-300 bg-amber-500/15 hover:bg-amber-500/25 border border-amber-400/30 transition flex items-center gap-1"
                  title="恢复上次对比"
                >
                  <RotateCcw className="w-3 h-3" />
                  <span className="hidden sm:inline">恢复上次</span>
                </button>
              )}

              <button
                onClick={onOpenModal}
                disabled={compareProducts.length < 2}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 transition ${
                  compareProducts.length >= 2
                    ? 'bg-amber-400 text-zinc-950 hover:bg-amber-300 shadow-xs'
                    : 'bg-zinc-800 text-zinc-500 cursor-not-allowed'
                }`}
              >
                <span>开始对比</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => setIsMinimized(false)}
                className="p-1.5 rounded-lg text-zinc-400 hover:text-white"
              >
                <ChevronUp className="w-4 h-4" />
              </button>
            </div>
          </div>
        ) : (
          /* Expanded full bar */
          <div className="p-4 sm:p-5 space-y-3">
            {/* Top row */}
            <div className="flex items-center justify-between border-b border-zinc-800 pb-2.5">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-lg bg-white/10 text-amber-400 flex items-center justify-center">
                  <Scale className="w-3.5 h-3.5" />
                </div>
                <span className="text-xs font-bold tracking-tight">商品参数对比栏</span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-white/15 text-zinc-300 font-semibold">
                  {compareProducts.length}/3 款
                </span>
                {compareProducts.length === 1 && (
                  <span className="text-[11px] text-amber-300/90 hidden sm:inline">
                    （再选 1~2 款商品即可开始深度对比）
                  </span>
                )}
              </div>

              {/* Action Buttons in Header */}
              <div className="flex items-center gap-1.5">
                {/* 恢复上次对比按钮 */}
                {isLastDifferentFromCurrent && onRestoreLastCompare && (
                  <button
                    onClick={onRestoreLastCompare}
                    className="px-2.5 py-1 rounded-lg text-[11px] font-semibold text-amber-300 hover:text-amber-200 bg-amber-400/10 hover:bg-amber-400/20 border border-amber-400/30 transition flex items-center gap-1"
                    title={`恢复上次对比 (${lastComparedProducts.length}款商品)`}
                  >
                    <RotateCcw className="w-3 h-3 text-amber-400" />
                    <span>恢复上次对比</span>
                  </button>
                )}

                {/* 对比历史下拉入口 */}
                {compareHistory && compareHistory.length > 0 && (
                  <button
                    onClick={() => setShowHistoryPanel(!showHistoryPanel)}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-medium transition flex items-center gap-1 ${
                      showHistoryPanel
                        ? 'bg-zinc-700 text-white'
                        : 'text-zinc-400 hover:text-zinc-200 hover:bg-white/5'
                    }`}
                    title="查看对比历史记录"
                  >
                    <History className="w-3 h-3 text-amber-400" />
                    <span>历史 ({compareHistory.length})</span>
                  </button>
                )}

                {/* 清空按钮 */}
                <button
                  onClick={onClearAll}
                  className="px-2.5 py-1 rounded-lg text-[11px] text-zinc-400 hover:text-rose-400 hover:bg-white/5 transition flex items-center gap-1"
                >
                  <Trash2 className="w-3 h-3" />
                  <span>清空</span>
                </button>

                {/* 最小化按钮 */}
                <button
                  onClick={() => setIsMinimized(true)}
                  className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-white/5 transition"
                  title="最小化"
                >
                  <ChevronDown className="w-3.5 h-3.5" />
                </button>

                {/* 关闭对比栏按钮 */}
                <button
                  onClick={onCloseBar}
                  className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-white/5 transition"
                  title="关闭对比栏"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            {/* History Dropdown Overlay inside Expanded Bar */}
            {showHistoryPanel && compareHistory.length > 0 && (
              <div
                ref={historyRef}
                className="bg-zinc-950/90 border border-zinc-700/80 rounded-2xl p-3 space-y-2 animate-in fade-in slide-in-from-top-2 duration-200"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-zinc-300 flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 text-amber-400" />
                    对比历史记录库（最近对比组合）
                  </span>
                  <div className="flex items-center gap-2">
                    {onClearHistory && (
                      <button
                        onClick={onClearHistory}
                        className="text-[10px] text-zinc-500 hover:text-rose-400 transition"
                      >
                        清空历史记录
                      </button>
                    )}
                    <button
                      onClick={() => setShowHistoryPanel(false)}
                      className="p-1 text-zinc-500 hover:text-zinc-300"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                </div>

                <div className="max-h-52 overflow-y-auto space-y-1.5 pr-1 custom-scrollbar">
                  {compareHistory.map((item) => {
                    const isCurrent =
                      item.products.length === compareProducts.length &&
                      item.products.every((p) =>
                        compareProducts.some((cp) => cp.id === p.id)
                      );

                    return (
                      <div
                        key={item.id}
                        className={`p-2.5 rounded-xl border flex items-center justify-between gap-3 transition ${
                          isCurrent
                            ? 'bg-zinc-800/90 border-amber-400/40'
                            : 'bg-zinc-900 border-zinc-800 hover:border-zinc-700'
                        }`}
                      >
                        <div className="flex items-center gap-2.5 min-w-0 flex-1">
                          <div className="flex -space-x-1.5 overflow-hidden shrink-0">
                            {item.products.map((p) => (
                              <img
                                key={p.id}
                                src={p.images[0]}
                                alt={p.title}
                                className="w-7 h-7 rounded-lg ring-1 ring-zinc-800 object-cover bg-zinc-900"
                              />
                            ))}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-[11px] font-semibold text-zinc-200 truncate">
                              {item.products.map((p) => p.title).join(' vs ')}
                            </p>
                            <div className="flex items-center gap-2 text-[10px] text-zinc-500 mt-0.5">
                              <span>{formatTimeAgo(item.timestamp)}</span>
                              <span>·</span>
                              <span>{item.products.length} 款商品</span>
                              {isCurrent && (
                                <span className="text-amber-400 font-medium">（当前对比中）</span>
                              )}
                            </div>
                          </div>
                        </div>

                        <div className="flex items-center gap-1.5 shrink-0">
                          <button
                            onClick={() => {
                              if (onRestoreFromHistory) {
                                onRestoreFromHistory(item.products.map((p) => p.id));
                                setShowHistoryPanel(false);
                              }
                            }}
                            disabled={isCurrent}
                            className={`px-2.5 py-1 rounded-lg text-[10px] font-bold flex items-center gap-1 transition ${
                              isCurrent
                                ? 'bg-zinc-800 text-zinc-500 cursor-default'
                                : 'bg-amber-400 text-zinc-950 hover:bg-amber-300'
                            }`}
                          >
                            <RotateCcw className="w-2.5 h-2.5" />
                            <span>{isCurrent ? '当前正对比' : '应用此对比'}</span>
                          </button>
                          {onDeleteHistoryItem && (
                            <button
                              onClick={() => onDeleteHistoryItem(item.id)}
                              className="p-1 text-zinc-500 hover:text-rose-400 transition"
                              title="删除此条历史"
                            >
                              <X className="w-3 h-3" />
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Slots Grid */}
            <div className="grid grid-cols-3 gap-2.5 sm:gap-3">
              {compareProducts.map((prod) => (
                <div
                  key={prod.id}
                  className="bg-zinc-800/90 rounded-2xl p-2.5 border border-zinc-700/60 flex items-center gap-2.5 relative group"
                >
                  <button
                    onClick={() => onRemoveProduct(prod.id)}
                    className="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-zinc-700 text-zinc-300 hover:bg-rose-600 hover:text-white flex items-center justify-center text-[10px] shadow-sm transition"
                    title="移除"
                  >
                    <X className="w-3 h-3" />
                  </button>
                  <img
                    src={prod.images[0]}
                    alt={prod.title}
                    className="w-10 h-10 rounded-xl object-cover bg-zinc-900 border border-zinc-700 shrink-0"
                  />
                  <div className="min-w-0 flex-1">
                    <span className="text-[11px] font-semibold text-zinc-100 block truncate">
                      {prod.title}
                    </span>
                    <span className="text-[11px] font-black text-amber-400">
                      ¥{prod.price}
                    </span>
                  </div>
                </div>
              ))}

              {/* Empty placeholder slots */}
              {Array.from({ length: 3 - compareProducts.length }).map((_, idx) => (
                <div
                  key={`empty-${idx}`}
                  className="rounded-2xl border-2 border-dashed border-zinc-800 p-2.5 flex items-center justify-center text-center text-zinc-500 bg-zinc-900/40 select-none"
                >
                  <div className="flex items-center gap-1.5 text-xs text-zinc-500">
                    <Plus className="w-3.5 h-3.5 text-zinc-600" />
                    <span className="text-[11px]">还可加 {3 - compareProducts.length - idx} 款</span>
                  </div>
                </div>
              ))}
            </div>

            {/* Bottom action bar */}
            <div className="flex items-center justify-between pt-1">
              <div className="flex items-center gap-2">
                <span className="text-[11px] text-zinc-400">
                  {compareProducts.length < 2
                    ? '请至少勾选 2 款商品以进行参数比对'
                    : `已满足对比条件，点击右侧按钮查看全维参数报告`}
                </span>
              </div>

              <button
                onClick={onOpenModal}
                disabled={compareProducts.length < 2}
                className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5 transition ${
                  compareProducts.length >= 2
                    ? 'bg-amber-400 hover:bg-amber-300 text-zinc-950 shadow-md active:scale-95'
                    : 'bg-zinc-800 text-zinc-500 cursor-not-allowed'
                }`}
              >
                <span>立即对比参数</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
