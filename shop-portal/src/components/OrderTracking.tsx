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
  className?: string;
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
    label: '顺丰已发货',
    labelEn: 'Shipped',
    desc: '已由顺丰专车揽收并启运干线',
    icon: Truck,
  },
  {
    key: 'out_for_delivery',
    label: '派送中',
    labelEn: 'Out for Delivery',
    desc: '顺丰快递员正在派送途中',
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

// Generate realistic default tracking logs based on active step index
function generateDefaultLogs(
  stepIndex: number,
  orderNumber = 'ORD-2026-8899',
  trackingNumber = 'SF19837482910',
  addressStr = '上海市 浦东新区 科技大道 88 号'
): TrackingEvent[] {
  const logs: TrackingEvent[] = [];

  if (stepIndex >= 0) {
    logs.push({
      title: '订单提交成功，等待商家处理',
      time: '2026-08-29 10:15:30',
      completed: true,
      description: `订单 [${orderNumber}] 支付成功，资金已安全托管，系统已向Henfon智能仓下发拣选任务。`,
      location: 'Henfon商城云端中心',
      iconType: 'order',
    });
  }

  if (stepIndex >= 1) {
    logs.push({
      title: '华东智能中央仓已完成拣货打包',
      time: '2026-08-29 11:30:15',
      completed: true,
      description: '商品已完成防震气泡包装与电子防伪标签核验，出库封箱等待顺丰速运揽件。',
      location: '华东智能自动化中央1号仓',
      iconType: 'warehouse',
    });
  } else {
    logs.push({
      title: '仓库智能配货',
      time: '预计今天下午',
      completed: false,
      description: 'Henfon仓储中心正按订单顺序调度自动化拣选货位。',
      location: 'Henfon中央仓',
      iconType: 'warehouse',
    });
  }

  if (stepIndex >= 2) {
    logs.push({
      title: '顺丰速运已揽收，航空件启运中',
      time: '2026-08-29 14:20:00',
      completed: true,
      description: `顺丰速运单号 [${trackingNumber}] 揽收成功，快件已通过顺丰航空特快干线发往目的地分拨中心。`,
      location: '顺丰华东航空转运枢纽',
      iconType: 'transit',
    });
  } else {
    logs.push({
      title: '顺丰速运干线运输',
      time: '预计次日凌晨',
      completed: false,
      description: '快件将通过顺丰特快冷链/恒温航空专线发往目的地。',
      location: '顺丰航空干线',
      iconType: 'transit',
    });
  }

  if (stepIndex >= 3) {
    logs.push({
      title: '快件已到达目的地营业部，顺丰小哥正在派送',
      time: '2026-08-30 08:45:10',
      completed: true,
      description: `快件已到达目的地营业点，顺丰快递员【张师傅 (138-1234-5678)】正在派送中，请注意接听电话。`,
      location: '顺丰速运目的地科技园营业部',
      iconType: 'courier',
    });
  } else {
    logs.push({
      title: '末端派送',
      time: '预计次日上午',
      completed: false,
      description: '顺丰快递员将进行送前电联并送货上门。',
      location: '目的地营业网点',
      iconType: 'courier',
    });
  }

  if (stepIndex >= 4) {
    logs.push({
      title: '包裹已签收，感谢您的信任！',
      time: '2026-08-30 10:12:45',
      completed: true,
      description: `快件已妥投并由【本人/智能门锁签收】，如有任何售后问题请随时联系Henfon商城 7×24 小时管家客服。`,
      location: addressStr,
      iconType: 'done',
    });
  } else {
    logs.push({
      title: '签收妥投',
      time: '预计 1-2 日内送达',
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
  carrier = '顺丰速运 SF Express (特快专递)',
  estimatedDelivery: propEstimatedDelivery,
  shippingAddress,
  trackingSteps,
  layout = 'combined',
  showDetails = true,
  showCarrierCard = true,
  interactiveSimulator = false,
  className = '',
}) => {
  // Derive effective status
  const currentStatusString = order ? order.status : status || 'paid';
  const initialStepIndex = getStatusStepIndex(currentStatusString);

  // Interactive state for simulation
  const [simulatedIndex, setSimulatedIndex] = useState<number>(initialStepIndex);
  const [copied, setCopied] = useState(false);
  const [isLogsExpanded, setIsLogsExpanded] = useState(true);

  const activeIndex = interactiveSimulator ? simulatedIndex : initialStepIndex;

  const orderNumber = order?.orderNumber || propOrderNumber || 'ORD-2026-889921';
  const trackingNumber = order?.trackingNumber || propTrackingNumber || 'SF19837482910';
  const estimatedDelivery =
    order?.estimatedDelivery || propEstimatedDelivery || '预计 1-2 日内顺丰送达';

  const address = order?.shippingAddress || shippingAddress;
  const addressText = address
    ? `${address.province} ${address.city} ${address.district} ${address.detail}`
    : '上海市 浦东新区 科技大道 88 号';

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

  const activeMilestone = MILESTONES[activeIndex] || MILESTONES[0];
  const progressPercent = (activeIndex / (MILESTONES.length - 1)) * 100;

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
                  {carrier}
                </span>
                <span className="text-xs font-mono text-zinc-400">
                  单号: <strong className="text-zinc-200">{trackingNumber}</strong>
                </span>
                <button
                  type="button"
                  onClick={handleCopyTracking}
                  className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-white transition flex items-center gap-1 text-[11px]"
                  title="复制顺丰运单号"
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
              </div>

              <div className="flex items-center gap-2 text-sm font-bold text-white pt-0.5">
                <span>当前状态：</span>
                <span className="text-emerald-400 font-extrabold flex items-center gap-1.5">
                  <span className="relative flex h-2.5 w-2.5">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
                  </span>
                  {activeMilestone.label} ({activeMilestone.labelEn})
                </span>
              </div>
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
      {(layout === 'horizontal' || layout === 'combined') && (
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
                      <span className="text-zinc-500 font-mono">{address.phone}</span>
                      {address.tag && (
                        <span className="text-[10px] px-1.5 rounded bg-zinc-200 text-zinc-700">
                          {address.tag}
                        </span>
                      )}
                    </div>
                    <p className="text-[11px] text-zinc-500 mt-0.5 leading-relaxed truncate sm:whitespace-normal">
                      {address.province} {address.city} {address.district} {address.detail}
                    </p>
                  </div>
                </div>
              )}

              {/* Vertical Step Nodes */}
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

              {/* Security & Official Customer Care Footer */}
              <div className="pt-3 border-t border-zinc-100 flex flex-col sm:flex-row items-center justify-between gap-2 text-[11px] text-zinc-400">
                <div className="flex items-center gap-1.5">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-500" />
                  <span>顺丰保价与全天候运输监控中</span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="flex items-center gap-1 hover:text-zinc-700 cursor-pointer">
                    <Phone className="w-3 h-3" />
                    顺丰专线 95338
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
