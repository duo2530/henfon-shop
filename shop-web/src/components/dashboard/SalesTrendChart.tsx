import React, { useState } from 'react';
import {
  ResponsiveContainer,
  ComposedChart,
  Area,
  Bar,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts';
import { salesTrends7Days, salesTrends30Days } from '../../data/mockData';
import { DailySalesData } from '../../types';
import { 
  TrendingUp, 
  BarChart3, 
  Layers, 
  DollarSign, 
  Package, 
  Calendar,
  Sparkles
} from 'lucide-react';

interface SalesTrendChartProps {
  className?: string;
}

export const SalesTrendChart: React.FC<SalesTrendChartProps> = ({ className = '' }) => {
  const [timeRange, setTimeRange] = useState<'7days' | '30days'>('7days');
  const [chartViewMode, setChartViewMode] = useState<'composed' | 'amount' | 'volume'>('composed');

  const currentData: DailySalesData[] = timeRange === '7days' ? salesTrends7Days : salesTrends30Days;

  // Aggregate Metrics for Selected Period
  const totalSales = currentData.reduce((acc, curr) => acc + curr.sales, 0);
  const totalVolume = currentData.reduce((acc, curr) => acc + (curr.volume || 0), 0);
  const totalOrders = currentData.reduce((acc, curr) => acc + curr.orders, 0);
  const avgDailySales = Math.round(totalSales / currentData.length);
  const avgDailyVolume = Math.round(totalVolume / currentData.length);

  // Custom Recharts Tooltip
  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      const dataPoint: DailySalesData = payload[0]?.payload;
      return (
        <div className="bg-gray-900/95 text-white p-3.5 rounded-xl shadow-xl border border-gray-700/60 backdrop-blur-md min-w-[200px] text-xs">
          <div className="flex items-center justify-between pb-2 mb-2 border-b border-gray-700">
            <span className="font-semibold text-gray-200">{label}</span>
            {dataPoint?.fullDate && (
              <span className="text-[10px] text-gray-400 font-mono">{dataPoint.fullDate}</span>
            )}
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between gap-4">
              <span className="flex items-center gap-1.5 text-blue-400 font-medium">
                <span className="w-2.5 h-2.5 rounded-full bg-blue-500 inline-block"></span>
                订单金额:
              </span>
              <span className="font-bold text-white font-mono">
                ¥{dataPoint.sales.toLocaleString()}
              </span>
            </div>

            <div className="flex items-center justify-between gap-4">
              <span className="flex items-center gap-1.5 text-emerald-400 font-medium">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 inline-block"></span>
                商品销量:
              </span>
              <span className="font-bold text-emerald-200 font-mono">
                {dataPoint.volume.toLocaleString()} 件
              </span>
            </div>

            <div className="flex items-center justify-between gap-4 pt-1 border-t border-gray-800 text-[11px] text-gray-300">
              <span className="text-gray-400">成交订单数:</span>
              <span className="font-mono">{dataPoint.orders} 单</span>
            </div>

            {dataPoint.avgOrderValue && (
              <div className="flex items-center justify-between gap-4 text-[11px] text-gray-300">
                <span className="text-gray-400">平均笔单价:</span>
                <span className="font-mono">¥{dataPoint.avgOrderValue.toFixed(1)}</span>
              </div>
            )}
          </div>
        </div>
      );
    }
    return null;
  };

  return (
    <div className={`bg-white rounded-xl border border-[#E2E8F0] p-5 shadow-xs flex flex-col justify-between ${className}`}>
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
              <BarChart3 className="w-4 h-4" />
            </div>
            <h3 className="text-base font-bold text-[#191C1E] tracking-tight">
              销售趋势统计 (Sales Trends)
            </h3>
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-blue-50 text-blue-700 border border-blue-100">
              <Sparkles className="w-3 h-3 text-blue-600" />
              Recharts 动态可视化
            </span>
          </div>
          <p className="text-xs text-gray-500 mt-1">
            展示{timeRange === '7days' ? '最近 7 天' : '最近 30 天'}的订单金额（¥）与商品销量（件）走势变化
          </p>
        </div>

        {/* Action Controls: Chart Mode & Time Range */}
        <div className="flex items-center flex-wrap gap-2">
          {/* View Mode Toggle */}
          <div className="inline-flex rounded-lg p-0.5 bg-gray-100 border border-gray-200 text-xs font-medium">
            <button
              onClick={() => setChartViewMode('composed')}
              className={`px-2.5 py-1.5 rounded-md transition-all flex items-center gap-1 ${
                chartViewMode === 'composed'
                  ? 'bg-white text-blue-600 font-semibold shadow-2xs'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
              title="双轴综合走势"
            >
              <Layers className="w-3.5 h-3.5" />
              <span>综合走势</span>
            </button>
            <button
              onClick={() => setChartViewMode('amount')}
              className={`px-2.5 py-1.5 rounded-md transition-all flex items-center gap-1 ${
                chartViewMode === 'amount'
                  ? 'bg-white text-blue-600 font-semibold shadow-2xs'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
              title="仅看订单金额"
            >
              <DollarSign className="w-3.5 h-3.5" />
              <span>订单金额</span>
            </button>
            <button
              onClick={() => setChartViewMode('volume')}
              className={`px-2.5 py-1.5 rounded-md transition-all flex items-center gap-1 ${
                chartViewMode === 'volume'
                  ? 'bg-white text-emerald-600 font-semibold shadow-2xs'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
              title="仅看商品销量"
            >
              <Package className="w-3.5 h-3.5" />
              <span>商品销量</span>
            </button>
          </div>

          {/* Time Range Toggle */}
          <div className="inline-flex rounded-lg p-0.5 bg-gray-100 border border-gray-200 text-xs font-medium">
            <button
              onClick={() => setTimeRange('7days')}
              className={`px-3 py-1.5 rounded-md transition-all flex items-center gap-1 ${
                timeRange === '7days'
                  ? 'bg-white text-blue-600 font-semibold shadow-2xs'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              <Calendar className="w-3.5 h-3.5" />
              <span>近 7 天</span>
            </button>
            <button
              onClick={() => setTimeRange('30days')}
              className={`px-3 py-1.5 rounded-md transition-all flex items-center gap-1 ${
                timeRange === '30days'
                  ? 'bg-white text-blue-600 font-semibold shadow-2xs'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              <Calendar className="w-3.5 h-3.5" />
              <span>近 30 天</span>
            </button>
          </div>
        </div>
      </div>

      {/* Metric Quick Glance Chips */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
        <div className="p-2.5 rounded-lg bg-blue-50/60 border border-blue-100 flex flex-col justify-between">
          <span className="text-[11px] text-gray-500 font-medium">
            {timeRange === '7days' ? '7天累计金额' : '30天累计金额'}
          </span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base sm:text-lg font-bold text-blue-700 font-mono">
              ¥{totalSales.toLocaleString()}
            </span>
            <span className="text-[10px] font-semibold text-emerald-600 flex items-center">
              <TrendingUp className="w-3 h-3 mr-0.5" /> +14.8%
            </span>
          </div>
        </div>

        <div className="p-2.5 rounded-lg bg-emerald-50/60 border border-emerald-100 flex flex-col justify-between">
          <span className="text-[11px] text-gray-500 font-medium">
            {timeRange === '7days' ? '7天商品销量' : '30天商品销量'}
          </span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base sm:text-lg font-bold text-emerald-700 font-mono">
              {totalVolume.toLocaleString()} <span className="text-xs font-normal">件</span>
            </span>
            <span className="text-[10px] font-semibold text-emerald-600 flex items-center">
              <TrendingUp className="w-3 h-3 mr-0.5" /> +11.2%
            </span>
          </div>
        </div>

        <div className="p-2.5 rounded-lg bg-indigo-50/60 border border-indigo-100 flex flex-col justify-between">
          <span className="text-[11px] text-gray-500 font-medium">日均成交金额</span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base sm:text-lg font-bold text-indigo-700 font-mono">
              ¥{avgDailySales.toLocaleString()}
            </span>
            <span className="text-[10px] text-gray-500">/天</span>
          </div>
        </div>

        <div className="p-2.5 rounded-lg bg-amber-50/60 border border-amber-100 flex flex-col justify-between">
          <span className="text-[11px] text-gray-500 font-medium">日均商品销量</span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base sm:text-lg font-bold text-amber-700 font-mono">
              {avgDailyVolume} <span className="text-xs font-normal">件</span>
            </span>
            <span className="text-[10px] text-gray-500">({totalOrders} 单)</span>
          </div>
        </div>
      </div>

      {/* Main Recharts Container */}
      <div className="w-full h-[280px] bg-gradient-to-b from-gray-50/50 to-white rounded-lg p-2 border border-gray-100">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart
            data={currentData}
            margin={{ top: 12, right: 12, left: 4, bottom: 4 }}
          >
            <defs>
              <linearGradient id="salesAmountGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#2563eb" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#2563eb" stopOpacity={0.0} />
              </linearGradient>
              <linearGradient id="volumeBarGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#10b981" stopOpacity={0.85} />
                <stop offset="100%" stopColor="#059669" stopOpacity={0.45} />
              </linearGradient>
            </defs>

            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" />

            <XAxis
              dataKey="date"
              tickLine={false}
              axisLine={{ stroke: '#CBD5E1' }}
              tick={{ fill: '#64748B', fontSize: 11 }}
            />

            {/* Left Y-Axis: 订单金额 (¥) */}
            {(chartViewMode === 'composed' || chartViewMode === 'amount') && (
              <YAxis
                yAxisId="left"
                orientation="left"
                tickLine={false}
                axisLine={false}
                tick={{ fill: '#2563eb', fontSize: 11 }}
                tickFormatter={(val) => `¥${val >= 1000 ? `${(val / 1000).toFixed(0)}k` : val}`}
                domain={['auto', 'auto']}
              />
            )}

            {/* Right Y-Axis: 商品销量 (件) */}
            {(chartViewMode === 'composed' || chartViewMode === 'volume') && (
              <YAxis
                yAxisId="right"
                orientation={chartViewMode === 'volume' ? 'left' : 'right'}
                tickLine={false}
                axisLine={false}
                tick={{ fill: '#059669', fontSize: 11 }}
                tickFormatter={(val) => `${val}件`}
                domain={['auto', 'auto']}
              />
            )}

            <Tooltip content={<CustomTooltip />} />

            <Legend
              verticalAlign="top"
              align="right"
              iconType="circle"
              wrapperStyle={{ paddingBottom: 10, fontSize: 12 }}
            />

            {/* Render based on selected View Mode */}
            {chartViewMode === 'composed' && (
              <>
                <Bar
                  yAxisId="right"
                  dataKey="volume"
                  name="商品销量 (件)"
                  fill="url(#volumeBarGradient)"
                  radius={[4, 4, 0, 0]}
                  barSize={18}
                />
                <Area
                  yAxisId="left"
                  type="monotone"
                  dataKey="sales"
                  name="订单金额 (¥)"
                  stroke="#2563eb"
                  strokeWidth={2.5}
                  fill="url(#salesAmountGradient)"
                  dot={{ r: 4, fill: '#ffffff', stroke: '#2563eb', strokeWidth: 2 }}
                  activeDot={{ r: 6, fill: '#2563eb', stroke: '#ffffff', strokeWidth: 2 }}
                />
                <Line
                  yAxisId="right"
                  type="monotone"
                  dataKey="volume"
                  name="销量折线趋势"
                  stroke="#059669"
                  strokeWidth={2}
                  dot={false}
                />
              </>
            )}

            {chartViewMode === 'amount' && (
              <>
                <Area
                  yAxisId="left"
                  type="monotone"
                  dataKey="sales"
                  name="订单金额 (¥)"
                  stroke="#2563eb"
                  strokeWidth={3}
                  fill="url(#salesAmountGradient)"
                  dot={{ r: 4, fill: '#ffffff', stroke: '#2563eb', strokeWidth: 2 }}
                  activeDot={{ r: 6, fill: '#2563eb', stroke: '#ffffff', strokeWidth: 2 }}
                />
              </>
            )}

            {chartViewMode === 'volume' && (
              <>
                <Bar
                  yAxisId="right"
                  dataKey="volume"
                  name="商品销量 (件)"
                  fill="url(#volumeBarGradient)"
                  radius={[6, 6, 0, 0]}
                  barSize={24}
                />
                <Line
                  yAxisId="right"
                  type="monotone"
                  dataKey="orders"
                  name="成单笔数 (单)"
                  stroke="#f59e0b"
                  strokeWidth={2.5}
                  dot={{ r: 3.5, fill: '#ffffff', stroke: '#f59e0b', strokeWidth: 2 }}
                />
              </>
            )}
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* Footer Info / Trend Highlights */}
      <div className="mt-3 pt-2.5 border-t border-gray-100 flex flex-wrap items-center justify-between text-xs text-gray-500 gap-2">
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
          本周销量峰值日: <strong className="text-gray-800">周六 (¥54,100 / 396件)</strong>
        </span>
        <span className="text-gray-400">
          数据每 10 分钟自动与订单及仓储数据库同步
        </span>
      </div>
    </div>
  );
};
