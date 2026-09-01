import React, { useEffect, useMemo, useState } from 'react';
import { 
  Users, 
  ShoppingBag, 
  DollarSign, 
  Package,
  RefreshCw
} from 'lucide-react';
import {
  getReportingDashboardMetrics,
  getReportingMemberAnalysis,
  getReportingSalesTrend,
  BackendReportingDashboardMetrics,
  BackendReportingMemberAnalysis,
  BackendReportingSalesTrendPoint,
} from '../../api/adminApi';
import { 
  AreaChart, 
  Area, 
  BarChart, 
  Bar, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer, 
} from 'recharts';

export const AnalyticsOverviewView: React.FC = () => {
  const [timeRange, setTimeRange] = useState<'today' | '7d' | '30d'>('today');
  const [dashboardMetrics, setDashboardMetrics] = useState<BackendReportingDashboardMetrics | null>(null);
  const [salesTrend, setSalesTrend] = useState<BackendReportingSalesTrendPoint[]>([]);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [overviewReloadKey, setOverviewReloadKey] = useState(0);
  const [memberAnalysis, setMemberAnalysis] = useState<BackendReportingMemberAnalysis | null>(null);
  const [memberAnalysisLoading, setMemberAnalysisLoading] = useState(false);
  const [memberAnalysisError, setMemberAnalysisError] = useState<string | null>(null);

  const memberAnalysisStartDate = useMemo(() => {
    const end = new Date();
    const days = timeRange === 'today' ? 0 : timeRange === '7d' ? 6 : 29;
    const start = new Date(end);
    start.setDate(end.getDate() - days);
    return start.toISOString().slice(0, 10);
  }, [timeRange]);

  useEffect(() => {
    let active = true;
    const endDate = new Date();
    const days = timeRange === 'today' ? 0 : timeRange === '7d' ? 6 : 29;
    const startDate = new Date(endDate);
    startDate.setDate(endDate.getDate() - days);
    const toDate = (value: Date) => value.toISOString().slice(0, 10);

    setOverviewLoading(true);
    setOverviewError(null);
    void Promise.all([
      // 首页指标始终取服务端业务当天，避免浏览器时区导致查询未来日期。
      getReportingDashboardMetrics(),
      getReportingSalesTrend(toDate(startDate), toDate(endDate)),
    ])
      .then(([metrics, trend]) => {
        if (!active) return;
        setDashboardMetrics(metrics);
        setSalesTrend(trend || []);
      })
      .catch((error) => {
        if (!active) return;
        setDashboardMetrics(null);
        setSalesTrend([]);
        setOverviewError(error instanceof Error ? error.message : '经营概览加载失败');
      })
      .finally(() => {
        if (active) setOverviewLoading(false);
      });
    return () => {
      active = false;
    };
  }, [timeRange, overviewReloadKey]);

  useEffect(() => {
    let active = true;
    setMemberAnalysisLoading(true);
    setMemberAnalysisError(null);
    void getReportingMemberAnalysis(memberAnalysisStartDate)
      .then((result) => {
        if (active) setMemberAnalysis(result);
      })
      .catch((error) => {
        if (active) {
          setMemberAnalysis(null);
          setMemberAnalysisError(error instanceof Error ? error.message : '会员分析加载失败');
        }
      })
      .finally(() => {
        if (active) setMemberAnalysisLoading(false);
      });
    return () => {
      active = false;
    };
  }, [memberAnalysisStartDate]);

  const memberLevelChartData = (memberAnalysis?.levelStats || []).map((item) => ({
    level: item.memberLevel,
    members: Number(item.memberCount || 0),
    active: Number(item.activeMemberCount || 0),
  }));

  const salesTrendChartData = salesTrend.map((item) => ({
    time: item.date ? item.date.slice(5) : '-',
    salesAmount: Number(item.salesAmount || 0),
    orderCount: Number(item.orderCount || 0),
  }));

  const formatAmount = (value?: number) => overviewLoading || overviewError || value === undefined
    ? '—'
    : `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  const formatCount = (value?: number) => overviewLoading || overviewError || value === undefined
    ? '—'
    : Number(value || 0).toLocaleString('zh-CN');

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              经营全景与流量大屏 (Analytics & BI)
            </h2>
            <span className="text-xs bg-indigo-50 text-indigo-700 font-semibold px-2 py-0.5 rounded-full border border-indigo-200">
              实时数据流
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            基于订单、商品和会员报表聚合，展示可追溯的经营趋势。
          </p>
        </div>

        <div className="flex items-center gap-2 bg-white p-1 rounded-lg border border-gray-200 shadow-2xs">
          <button
            onClick={() => setTimeRange('today')}
            className={`px-3 py-1 text-xs font-semibold rounded-md transition-colors ${
              timeRange === 'today' ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            今日实时
          </button>
          <button
            onClick={() => setTimeRange('7d')}
            className={`px-3 py-1 text-xs font-semibold rounded-md transition-colors ${
              timeRange === '7d' ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            近 7 天
          </button>
          <button
            onClick={() => setTimeRange('30d')}
            className={`px-3 py-1 text-xs font-semibold rounded-md transition-colors ${
              timeRange === '30d' ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            近 30 天
          </button>
        </div>
      </div>

      {overviewError && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 flex items-center justify-between gap-3" role="alert">
          <span>经营概览加载失败：{overviewError}</span>
          <button type="button" onClick={() => setOverviewReloadKey((value) => value + 1)} className="inline-flex items-center gap-1 rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-100">
            <RefreshCw className="w-3.5 h-3.5" />重试
          </button>
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">今日销售额</span>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">{formatAmount(dashboardMetrics?.todaySalesAmount)}</div>
          <div className="text-xs text-gray-400 mt-1">已支付且未退款订单</div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">今日订单数</span>
            <div className="p-2 bg-purple-50 text-purple-600 rounded-lg">
              <Package className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">{formatCount(dashboardMetrics?.todayOrderCount)}</div>
          <div className="text-xs text-gray-400 mt-1">按订单创建日期统计</div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">今日新增会员</span>
            <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
              <Users className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-emerald-700 mt-1">{formatCount(dashboardMetrics?.todayMemberCount)}</div>
          <div className="text-xs text-gray-400 mt-1">按会员注册日期统计</div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">累计商品数</span>
            <div className="p-2 bg-amber-50 text-amber-600 rounded-lg">
              <ShoppingBag className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-amber-700 mt-1">{formatCount(dashboardMetrics?.totalProductCount)}</div>
          <div className="text-xs text-gray-400 mt-1">截至今日已创建商品</div>
        </div>
      </div>

      {/* Main Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Traffic & GMV Hourly Trend (2 cols) */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-sm font-bold text-gray-900">销售额与订单趋势</h3>
              <p className="text-xs text-gray-400">按支付时间聚合，日期范围与上方筛选一致</p>
            </div>
            <div className="flex items-center gap-4 text-xs font-medium text-gray-600">
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-blue-600" /> 销售额
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-purple-500" /> 成交订单数
              </span>
            </div>
          </div>

          <div className="h-[280px] w-full">
            {overviewLoading ? <div className="h-full flex items-center justify-center text-sm text-gray-400">正在加载销售趋势...</div> : salesTrendChartData.length === 0 ? <div className="h-full flex items-center justify-center text-sm text-gray-400">当前范围暂无销售趋势数据</div> : <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={salesTrendChartData}>
                <defs>
                    <linearGradient id="salesGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2563EB" stopOpacity={0.25} />
                    <stop offset="95%" stopColor="#2563EB" stopOpacity={0} />
                  </linearGradient>
                    <linearGradient id="ordersGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#8B5CF6" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#8B5CF6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#F1F5F9" />
                <XAxis dataKey="time" stroke="#94A3B8" fontSize={11} tickLine={false} />
                <YAxis stroke="#94A3B8" fontSize={11} tickLine={false} axisLine={false} />
                <Tooltip
                  formatter={(value: any, name: any) => {
                    if (name === 'salesAmount') return [`¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`, '销售额'];
                    if (name === 'orderCount') return [`${Number(value).toLocaleString('zh-CN')} 单`, '成交订单数'];
                    return [value, name];
                  }}
                  contentStyle={{
                    backgroundColor: '#FFFFFF',
                    borderRadius: '8px',
                    border: '1px solid #E2E8F0',
                    fontSize: '12px',
                    boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)'
                  }}
                />
                <Area type="monotone" dataKey="salesAmount" stroke="#2563EB" strokeWidth={2} fillOpacity={1} fill="url(#salesGradient)" />
                <Area type="monotone" dataKey="orderCount" stroke="#8B5CF6" strokeWidth={2} fillOpacity={1} fill="url(#ordersGradient)" />
              </AreaChart>
            </ResponsiveContainer>}
          </div>
        </div>

        <div className="bg-white rounded-xl border border-dashed border-[#CBD5E1] shadow-xs p-5 flex flex-col justify-center">
          <h3 className="text-sm font-bold text-gray-900">渠道来源分析</h3>
          <p className="mt-2 text-xs leading-5 text-gray-500">当前报表接口尚未接入访问埋点，暂不展示估算占比。接入渠道埋点后将在此处显示真实来源数据。</p>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-dashed border-[#CBD5E1] shadow-xs p-5">
        <h3 className="text-sm font-bold text-gray-900 mb-1">用户转化漏斗</h3>
        <p className="text-xs text-gray-500">当前报表接口未采集页面浏览、加购和结算埋点，暂不使用静态数据填充漏斗。</p>
      </div>

      {/* Real member analysis */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5">
        <div className="flex items-center justify-between gap-3 mb-4">
          <div>
            <h3 className="text-sm font-bold text-gray-900">会员经营分析（真实数据）</h3>
            <p className="text-xs text-gray-400">统计范围：{memberAnalysis?.startDate || memberAnalysisStartDate} 至 {memberAnalysis?.endDate || '今天'}</p>
          </div>
          {memberAnalysisLoading && <span className="text-xs text-blue-600">加载中...</span>}
        </div>
        {memberAnalysisError ? (
          <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
            {memberAnalysisError}，请确认已登录且具备报表权限。
          </div>
        ) : memberAnalysis ? (
          <>
            <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-5">
              {[
                ['会员总数', memberAnalysis.totalMemberCount],
                ['新增会员', memberAnalysis.newMemberCount],
                ['活跃会员', memberAnalysis.activeMemberCount],
                ['复购率', `${Number(memberAnalysis.repurchaseRate || 0).toFixed(2)}%`],
                ['支付金额', `¥${Number(memberAnalysis.paidAmount || 0).toFixed(2)}`],
              ].map(([label, value]) => (
                <div key={String(label)} className="rounded-lg bg-slate-50 border border-slate-100 p-3">
                  <div className="text-xs text-gray-500">{label}</div>
                  <div className="mt-1 text-lg font-bold text-slate-900">{value}</div>
                </div>
              ))}
            </div>
            <div className="h-[220px] w-full">
              {memberLevelChartData.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={memberLevelChartData}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#F1F5F9" />
                    <XAxis dataKey="level" stroke="#94A3B8" fontSize={11} tickLine={false} />
                    <YAxis stroke="#94A3B8" fontSize={11} tickLine={false} axisLine={false} allowDecimals={false} />
                    <Tooltip />
                    <Bar dataKey="members" name="会员数" fill="#6366F1" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="active" name="活跃会员" fill="#10B981" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-xs text-gray-400">当前范围暂无会员等级数据</div>
              )}
            </div>
          </>
        ) : (
          <div className="py-10 text-center text-xs text-gray-400">暂无会员分析数据</div>
        )}
      </div>
    </div>
  );
};
