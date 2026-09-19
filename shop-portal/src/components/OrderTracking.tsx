import React, { useState } from 'react';
import {
  ClipboardCheck,
  Package,
  Truck,
  MapPin,
  CheckCircle2,
  Clock,
  Copy,
  Check,
  Phone,
  ShieldCheck,
  ChevronDown,
  ChevronUp,
  Navigation,
  Box,
  Sparkles,
} from 'lucide-react';
import { Order, Address } from '../types/ecommerce';

export type ShipmentStage =
  | 'placed'
  | 'processing'
  | 'shipped'
  | 'out_for_delivery'
  | 'delivered';

export interface TrackingEvent {
  title: string;
  time?: string;
  completed?: boolean;
  description?: string;
  location?: string;
  iconType?: 'order' | 'warehouse' | 'transit' | 'courier' | 'done';
}

export interface OrderTrackingProps {
  order?: Order;
  status?: string;
  orderNumber?: string;
  trackingNumber?: string;
  carrier?: string;
  estimatedDelivery?: string;
  shippingAddress?: Address;
  trackingSteps?: TrackingEvent[];
  layout?: 'horizontal' | 'vertical' | 'combined';
  showDetails?: boolean;
  showCarrierCard?: boolean;
  interactiveSimulator?: boolean;
  onRetryLogistics?: () => Promise<void> | void;
  logisticsRetrying?: boolean;
  className?: string;
}

/** 对订单展示中的手机号做脱敏，避免物流页面暴露完整联系方式。 */
export function maskPhone(phone?: string): string {
  if (!phone) return '-';
  const normalized = phone.replace(/\s+/g, '');
  return normalized.length >= 7 ? `${normalized.slice(0, 3)}****${normalized.slice(-4)}` : normalized;
}

/** 对订单展示中的详细地址做脱敏，仅保留定位所需的省市区信息。 */
export function maskDetailAddress(detail?: string): string {
  if (!detail) return '';
  const normalized = detail.trim();
  if (normalized.length <= 6) return '******';
  return `${normalized.slice(0, 2)}******${normalized.slice(-2)}`;
}

// Canonical definition of 5 tracking milestone steps
interface MilestoneConfig {
  key: ShipmentStage;
  label: string;
  labelEn: string;
  desc: string;
  icon: React.FC<{ className?: string }>;
}

const MILESTONES: MilestoneConfig[] = [
  {
    key: 'placed',
    label: '已下单',
    labelEn: 'Order Placed',
    desc: '订单已成功提交并托管付款',
    icon: ClipboardCheck,
  },
  {
    key: 'processing',
    label: '仓库处理',
    labelEn: 'Processing',
    desc: '中央仓智能配货打包装箱',
    icon: Box,
  },
  {
    key: 'shipped',
    label: '已发货',
    labelEn: 'Shipped',
    desc: '包裹已交付承运商并启运干线',
    icon: Truck,
  },
  {
    key: 'out_for_delivery',
    label: '派送中',
    labelEn: 'Out for Delivery',
    desc: '快递员正在派送途中',
    icon: Navigation,
  },
  {
    key: 'delivered',
    label: '已签收',
    labelEn: 'Delivered',
    desc: '已妥投收件人并完成服务',
    icon: CheckCircle2,
  },
];

/** 已终结的订单状态文案：这类订单不再有物流动作，阶段条改为展示订单状态。 */
const CLOSED_STATUS_TEXT: Record<string, string> = {
  cancelled: '订单已取消',
  refunding: '退款处理中',
  refunded: '已退款',
};

// Helper to normalize any incoming status string to 0..4 step index
export function getStatusStepIndex(rawStatus?: string): number {
  if (!rawStatus) return 0;
  const s = rawStatus.toLowerCase().trim();

  if (s.includes('deliver') || s.includes('completed') || s.includes('签收') || s.includes('妥投') || s === 'done') {
    return 4; // Delivered
  }
  if (s.includes('out_for_delivery') || s.includes('delivering') || s.includes('派送') || s.includes('派件')) {
    return 3; // Out for Delivery
  }
  if (s.includes('ship') || s.includes('transit') || s.includes('发货') || s.includes('运输') || s.includes('揽收')) {
    return 2; // Shipped
  }
  if (s.includes('process') || s.includes('prepar') || s.includes('pack') || s.includes('配货') || s.includes('打包') || s.includes('拣货')) {
    return 1; // Processing
  }
  if (s.includes('paid') || s.includes('placed') || s.includes('order') || s.includes('付款') || s.includes('下单')) {
    return 0; // Order Placed (paid)
  }
  return 0;
}

/**
 * 服务端未返回轨迹时，按订单进度生成占位节点。
 *
 * 只描述环节本身，不编造承运商、运单号、时间与派件员信息 —— 这些一旦写死就会
 * 与后台真实数据（如中通/圆通承运）冲突，属于「假数据」而非演示。
 */
function generateDefaultLogs(
  stepIndex: number,
  orderNumber = '',
  trackingNumber = '',
  addressStr = ''
): TrackingEvent[] {
  const logs: TrackingEvent[] = [];
  const orderTag = orderNumber ? `订单 ${orderNumber} ` : '订单 ';

  if (stepIndex >= 0) {
    logs.push({
      title: '订单提交成功，等待商家处理',
      time: '待同步',
      completed: true,
      description: `${orderTag}支付已确认，系统已下发拣货任务。`,
      location: 'Henfon 商城',
      iconType: 'order',
    });
  }

  if (stepIndex >= 1) {
    logs.push({
      title: '仓库已完成拣货打包',
      time: '待同步',
      completed: true,
      description: '商品已完成包装与出库核验，等待承运商揽件。',
      location: 'Henfon 中央仓',
      iconType: 'warehouse',
    });
  } else {
    logs.push({
      title: '仓库拣货中',
      time: '待更新',
      completed: false,
      description: '仓储中心正按订单顺序安排拣选与打包。',
      location: 'Henfon 中央仓',
      iconType: 'warehouse',
    });
  }

  if (stepIndex >= 2) {
    logs.push({
      title: '承运商已揽收，运输中',
      time: '待同步',
      completed: true,
      description: `${trackingNumber ? `运单号 ${trackingNumber} ` : ''}包裹已揽收并运往目的地分拨中心。`,
      location: '承运商转运中心',
      iconType: 'transit',
    });
  } else {
    logs.push({
      title: '等待承运商揽收',
      time: '待更新',
      completed: false,
      description: '包裹出库后将交由承运商运输。',
      location: '承运商转运中心',
      iconType: 'transit',
    });
  }

  if (stepIndex >= 3) {
    logs.push({
      title: '快件已到达目的地，正在派送',
      time: '待同步',
      completed: true,
      description: '快递员正在派送，请注意接听电话。',
      location: '目的地营业部',
      iconType: 'courier',
    });
  } else {
    logs.push({
      title: '末端派送',
      time: '待更新',
      completed: false,
      description: '快件到达目的地后由快递员送货上门。',
      location: '目的地营业部',
      iconType: 'courier',
    });
  }

  if (stepIndex >= 4) {
    logs.push({
      title: '包裹已签收，感谢您的信任！',
      time: '待同步',
      completed: true,
      description: '包裹已妥投签收，如有售后问题请联系 Henfon 商城客服。',
      location: addressStr,
      iconType: 'done',
    });
  } else {
    logs.push({
      title: '签收妥投',
      time: '待更新',
      completed: false,
      description: '收件时请核验外包装完好无损。',
      location: addressStr,
      iconType: 'done',
    });
  }

  return logs;
}

export const OrderTracking: React.FC<OrderTrackingProps> = ({
  order,
  status,
  orderNumber: propOrderNumber,
  trackingNumber: propTrackingNumber,
  carrier = '',
  estimatedDelivery: propEstimatedDelivery,
  shippingAddress,
  trackingSteps,
  layout = 'combined',
  showDetails = true,
  showCarrierCard = true,
  interactiveSimulator = false,
  onRetryLogistics,
  logisticsRetrying = false,
  className = '',
}) => {
  // 已终结的订单不会再有物流动作，阶段条改为展示订单状态，避免出现「已下单」这种与后台不符的进度。
  const rawStatus = (order ? order.status : status || '').toLowerCase();
  const closedStatusText = CLOSED_STATUS_TEXT[rawStatus];
  const initialStepIndex = closedStatusText ? -1 : getStatusStepIndex(rawStatus);

  // Interactive state for simulation
  const [simulatedIndex, setSimulatedIndex] = useState<number>(initialStepIndex);
  const [copied, setCopied] = useState(false);
  const [isLogsExpanded, setIsLogsExpanded] = useState(true);

  const activeIndex = interactiveSimulator ? simulatedIndex : initialStepIndex;

  // 承运商、运单号与时效一律取服务端数据：缺失时如实留空，不再回落成写死的顺丰值。
  const orderNumber = order?.orderNumber || propOrderNumber || '';
  const trackingNumber = order?.trackingNumber || propTrackingNumber || '';
  const effectiveCarrier = order?.carrier || carrier;
  const estimatedDelivery = order?.estimatedDelivery || propEstimatedDelivery || '以物流轨迹为准';

  const address = order?.shippingAddress || shippingAddress;
  const addressText = address
    ? `${address.province} ${address.city} ${address.district} ${address.detail}`
    : '';

  // Build event log list
  const effectiveLogs =
    trackingSteps && trackingSteps.length > 0
      ? trackingSteps
      : order?.trackingSteps && order.trackingSteps.length > 0
      ? order.trackingSteps.map((s, idx) => ({
          ...s,
          completed: idx <= activeIndex,
        }))
      : generateDefaultLogs(activeIndex, orderNumber, trackingNumber, addressText);

  // Copy tracking number handler
  const handleCopyTracking = (e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(trackingNumber);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const activeMilestone = activeIndex >= 0 ? MILESTONES[activeIndex] || MILESTONES[0] : null;
  const progressPercent = activeIndex >= 0 ? (activeIndex / (MILESTONES.length - 1)) * 100 : 0;

  return (
    <div className={`space-y-4 ${className}`}>
      {/* 1. Carrier & Status Overview Bar */}
      {showCarrierCard && (
        <div className="p-4 rounded-2xl bg-zinc-900 text-white shadow-md relative overflow-hidden">
          {/* Subtle background glow */}
          <div className="absolute top-0 right-0 w-64 h-64 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

          <div className="relative z-10 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="space-y-1">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-[11px] font-bold px-2 py-0.5 rounded-md bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center gap-1">
                  <Truck className="w-3 h-3" />
                  {effectiveCarrier || '承运商待分配'}
                </span>
                <span className="text-xs font-mono text-zinc-400">
                  单号: <strong className="text-zinc-200">{trackingNumber || '暂无运单号'}</strong>
                </span>
                {trackingNumber ? (
                  <button
                    type="button"
                    onClick={handleCopyTracking}
                    className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-white transition flex items-center gap-1 text-[11px]"
                    title="复制运单号"
                  >
                    {copied ? (
                      <>
                        <Check className="w-3 h-3 text-emerald-400" />
                        <span className="text-emerald-400">已复制</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3 h-3" />
                        <span>复制</span>
                      </>
                    )}
                  </button>
                ) : null}
              </div>

              <div className="flex items-center gap-2 text-sm font-bold text-white pt-0.5">
                <span>当前状态：</span>
                <span className="text-emerald-400 font-extrabold flex items-center gap-1.5">
                  <span className="relative flex h-2.5 w-2.5">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
                  </span>
                  {activeMilestone ? `${activeMilestone.label} (${activeMilestone.labelEn})` : closedStatusText}
                </span>
              </div>
              {onRetryLogistics && trackingNumber && (
                <button
                  type="button"
                  disabled={logisticsRetrying}
                  onClick={() => void onRetryLogistics()}
                  className="mt-2 rounded-md border border-zinc-700 px-2 py-1 text-[11px] font-semibold text-zinc-200 hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {logisticsRetrying ? '同步中…' : '刷新物流轨迹'}
                </button>
              )}
            </div>

            <div className="text-left sm:text-right border-t sm:border-t-0 pt-2 sm:pt-0 border-zinc-800">
              <div className="text-[11px] text-zinc-400 flex items-center sm:justify-end gap-1">
                <Clock className="w-3 h-3 text-zinc-400" />
                <span>时效预估</span>
              </div>
              <p className="text-xs font-semibold text-emerald-300 mt-0.5">{estimatedDelivery}</p>
            </div>
          </div>

          {/* Interactive Simulation Switcher (Optional) */}
          {interactiveSimulator && (
            <div className="mt-3 pt-3 border-t border-zinc-800 flex items-center justify-between text-xs">
              <span className="text-zinc-400 text-[11px] flex items-center gap-1">
                <Sparkles className="w-3 h-3 text-amber-400" />
                状态预览模拟器:
              </span>
              <div className="flex gap-1 flex-wrap">
                {MILESTONES.map((m, idx) => (
                  <button
                    key={m.key}
                    type="button"
                    onClick={() => setSimulatedIndex(idx)}
                    className={`px-2 py-0.5 rounded text-[10px] font-semibold transition ${
                      simulatedIndex === idx
                        ? 'bg-emerald-500 text-zinc-950 shadow-xs'
                        : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
                    }`}
                  >
                    {m.label}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* 2. Visual Milestone Progress Timeline (Horizontal Mode) */}
      {activeIndex >= 0 && (layout === 'horizontal' || layout === 'combined') && (
        <div className="p-5 rounded-2xl bg-white border border-zinc-200/90 shadow-xs space-y-4">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-bold text-zinc-900 flex items-center gap-2">
              <Navigation className="w-3.5 h-3.5 text-zinc-700" />
              物流履约全流程进度
            </h4>
            <span className="text-[11px] text-zinc-400 font-medium">
              第 {activeIndex + 1} / {MILESTONES.length} 阶段
            </span>
          </div>

          {/* Progress Nodes Container */}
          <div className="relative pt-2 pb-2">
            {/* Background connecting track */}
            <div className="absolute top-[26px] left-[5%] right-[5%] h-1 bg-zinc-100 rounded-full z-0" />

            {/* Completed active connecting fill */}
            <div
              className="absolute top-[26px] left-[5%] h-1 bg-emerald-500 transition-all duration-500 rounded-full z-0"
              style={{
                width: `${Math.max(0, (progressPercent * 0.9))}%`,
              }}
            />

            {/* Milestones grid */}
            <div className="grid grid-cols-5 relative z-10">
              {MILESTONES.map((m, idx) => {
                const Icon = m.icon;
                const isPassed = idx < activeIndex;
                const isCurrent = idx === activeIndex;
                const isPending = idx > activeIndex;

                return (
                  <div
                    key={m.key}
                    onClick={() => interactiveSimulator && setSimulatedIndex(idx)}
                    className={`flex flex-col items-center text-center group ${
                      interactiveSimulator ? 'cursor-pointer' : ''
                    }`}
                  >
                    {/* Node circle icon */}
                    <div
                      className={`w-9 h-9 sm:w-10 sm:h-10 rounded-full flex items-center justify-center transition-all duration-300 ${
                        isPassed
                          ? 'bg-emerald-500 text-white shadow-xs'
                          : isCurrent
                          ? 'bg-zinc-900 text-emerald-400 ring-4 ring-emerald-100 border-2 border-emerald-500 scale-110 shadow-md'
                          : 'bg-white text-zinc-300 border-2 border-zinc-200'
                      }`}
                    >
                      {isPassed ? (
                        <Check className="w-4 h-4 sm:w-5 sm:h-5 stroke-3" />
                      ) : (
                        <Icon className="w-4 h-4 sm:w-4.5 sm:h-4.5" />
                      )}
                    </div>

                    {/* Step Titles */}
                    <div className="mt-2.5 space-y-0.5 max-w-[90px]">
                      <p
                        className={`text-xs font-bold leading-tight transition ${
                          isCurrent
                            ? 'text-zinc-950 font-extrabold'
                            : isPassed
                            ? 'text-emerald-700'
                            : 'text-zinc-400'
                        }`}
                      >
                        {m.label}
                      </p>
                      <p className="text-[10px] text-zinc-400 hidden sm:block truncate">
                        {m.labelEn}
                      </p>
                    </div>

                    {/* Status indicator tag */}
                    <div className="mt-1">
                      {isCurrent && (
                        <span className="text-[9px] font-bold px-1.5 py-0.2 rounded-full bg-emerald-100 text-emerald-800 animate-pulse">
                          进行中
                        </span>
                      )}
                      {isPassed && (
                        <span className="text-[9px] font-medium text-emerald-600">已达成</span>
                      )}
                      {isPending && (
                        <span className="text-[9px] font-medium text-zinc-300">待完成</span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* 3. Detailed Event Logs (Vertical Timeline) */}
      {showDetails && (layout === 'vertical' || layout === 'combined') && (
        <div className="rounded-2xl bg-white border border-zinc-200/90 shadow-xs overflow-hidden">
          {/* Header Toggle */}
          <div
            onClick={() => setIsLogsExpanded(!isLogsExpanded)}
            className="p-4 bg-zinc-50/70 hover:bg-zinc-50 flex items-center justify-between cursor-pointer border-b border-zinc-100 transition select-none"
          >
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-emerald-600" />
              <h4 className="text-xs font-bold text-zinc-900">物流实时轨迹日志</h4>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-zinc-200/70 text-zinc-600 font-medium">
                {effectiveLogs.length} 条节点
              </span>
            </div>
            <div className="flex items-center gap-1 text-xs text-zinc-400 font-medium">
              <span>{isLogsExpanded ? '收起详情' : '展开详情'}</span>
              {isLogsExpanded ? (
                <ChevronUp className="w-3.5 h-3.5" />
              ) : (
                <ChevronDown className="w-3.5 h-3.5" />
              )}
            </div>
          </div>

          {/* Logs Body */}
          {isLogsExpanded && (
            <div className="p-5 space-y-4 animate-in fade-in duration-200">
              {/* Delivery Address Reminder */}
              {address && (
                <div className="p-3 rounded-xl bg-zinc-50 border border-zinc-100 text-xs text-zinc-600 flex items-start gap-2.5">
                  <MapPin className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-zinc-900">{address.receiverName}</span>
                      <span className="text-zinc-500 font-mono" title="联系方式已脱敏">{maskPhone(address.phone)}</span>
                      {address.tag && (
                        <span className="text-[10px] px-1.5 rounded bg-zinc-200 text-zinc-700">
                          {address.tag}
                        </span>
                      )}
                    </div>
                    <p className="text-[11px] text-zinc-500 mt-0.5 leading-relaxed truncate sm:whitespace-normal">
                      {address.province} {address.city} {address.district} {maskDetailAddress(address.detail)}
                    </p>
                  </div>
                </div>
              )}

              {/* Vertical Step Nodes */}
              {effectiveLogs.length === 0 ? (
                <div className="rounded-xl border border-dashed border-zinc-200 bg-zinc-50/60 px-4 py-6 text-center text-xs text-zinc-500">
                  暂无物流轨迹，节点同步后自动更新。
                </div>
              ) : (
              <div className="relative pl-6 space-y-5 before:absolute before:left-[11px] before:top-2 before:bottom-2 before:w-[2px] before:bg-zinc-100">
                {effectiveLogs.map((log, idx) => {
                  const isLatestCompleted = log.completed && (idx === 0 || effectiveLogs[idx - 1]?.completed === false || idx === effectiveLogs.filter(l => l.completed).length - 1);
                  const isDone = log.completed;

                  return (
                    <div key={idx} className="relative group">
                      {/* Left status bullet / node */}
                      <div
                        className={`absolute -left-6 top-0.5 w-5 h-5 rounded-full flex items-center justify-center transition-all ${
                          isDone
                            ? isLatestCompleted
                              ? 'bg-emerald-500 text-white ring-4 ring-emerald-100'
                              : 'bg-emerald-500 text-white'
                            : 'bg-white border-2 border-zinc-200 text-zinc-300'
                        }`}
                      >
                        {isDone ? (
                          <Check className="w-3 h-3 stroke-3" />
                        ) : (
                          <div className="w-1.5 h-1.5 rounded-full bg-zinc-300" />
                        )}
                      </div>

                      {/* Log content */}
                      <div className="space-y-1">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                          <span
                            className={`text-xs font-bold ${
                              isDone ? 'text-zinc-900' : 'text-zinc-400'
                            }`}
                          >
                            {log.title}
                          </span>
                          {log.time && (
                            <span className="text-[11px] font-mono text-zinc-400 shrink-0">
                              {log.time}
                            </span>
                          )}
                        </div>

                        {log.description && (
                          <p
                            className={`text-xs leading-relaxed ${
                              isDone ? 'text-zinc-600' : 'text-zinc-400'
                            }`}
                          >
                            {log.description}
                          </p>
                        )}

                        {log.location && (
                          <div className="text-[10px] text-zinc-400 flex items-center gap-1 pt-0.5">
                            <MapPin className="w-3 h-3 text-zinc-400" />
                            <span>{log.location}</span>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
              )}

              {/* Security & Official Customer Care Footer */}
              <div className="pt-3 border-t border-zinc-100 flex flex-col sm:flex-row items-center justify-between gap-2 text-[11px] text-zinc-400">
                <div className="flex items-center gap-1.5">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-500" />
                  <span>承运商运输监控与保价服务中</span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="flex items-center gap-1 hover:text-zinc-700 cursor-pointer">
                    <Phone className="w-3 h-3" />
                    承运商客服专线
                  </span>
                  <span>·</span>
                  <span className="hover:text-zinc-700 cursor-pointer">Henfon专属客服</span>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
