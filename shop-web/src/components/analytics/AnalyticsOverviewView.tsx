import React, { useEffect, useMemo, useState } from 'react';
import { 
  TrendingUp, 
  Users, 
  ShoppingBag, 
  DollarSign, 
  ArrowUpRight, 
  ArrowDownRight, 
  Activity, 
  PieChart as PieChartIcon, 
  Layers, 
  Smartphone, 
  Globe 
} from 'lucide-react';
import { getReportingMemberAnalysis, BackendReportingMemberAnalysis } from '../../api/adminApi';
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
  PieChart, 
  Pie, 
  Cell 
} from 'recharts';

const trafficHourly = [
  { time: '00:00', pv: 420, uv: 280, gmv: 3200 },
  { time: '03:00', pv: 180, uv: 110, gmv: 980 },
  { time: '06:00', pv: 520, uv: 390, gmv: 4100 },
  { time: '09:00', pv: 2800, uv: 1950, gmv: 24800 },
  { time: '12:00', pv: 4200, uv: 2900, gmv: 38900 },
  { time: '15:00', pv: 3600, uv: 2400, gmv: 31200 },
  { time: '18:00', pv: 4900, uv: 3400, gmv: 45600 },
  { time: '21:00', pv: 6800, uv: 4800, gmv: 68900 },
  { time: '23:00', pv: 3100, uv: 2100, gmv: 29400 }
];

const channelShare = [
  { name: '微信小程序 / 公众号', value: 48, color: '#10B981' },
  { name: '移动端 App 直达', value: 28, color: '#3B82F6' },
  { name: '抖音/小红书直播流', value: 16, color: '#F59E0B' },
  { name: '搜索引擎 & 网页版', value: 8, color: '#8B5CF6' }
];

const funnelData = [
  { stage: '1. 页面浏览 (PV)', count: '128,400', rate: '100%' },
  { stage: '2. 商品详情查看', count: '64,200', rate: '50.0%' },
  { stage: '3. 加入购物车 / 立即买', count: '19,260', rate: '15.0%' },
  { stage: '4. 提交订单结算', count: '8,988', rate: '7.0%' },
  { stage: '5. 成功支付完成', count: '6,420', rate: '5.0%' }
];

export const AnalyticsOverviewView: React.FC = () => {
  const [timeRange, setTimeRange] = useState<'today' | '7d' | '30d'>('today');
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
            实时流量全景走势、访客转化漏斗分析、全渠道来源占比与客单价测算。
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

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">今日全店 GMV</span>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">¥246,880</div>
          <div className="flex items-center gap-1 text-xs text-emerald-600 mt-1 font-semibold">
            <ArrowUpRight className="w-3.5 h-3.5" /> 较昨日同期 +18.4%
          </div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">全站访客数 (UV)</span>
            <div className="p-2 bg-purple-50 text-purple-600 rounded-lg">
              <Users className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">18,430</div>
          <div className="flex items-center gap-1 text-xs text-emerald-600 mt-1 font-semibold">
            <ArrowUpRight className="w-3.5 h-3.5" /> PV 浏览量 128,400 次
          </div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">全链路支付转化率</span>
            <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
              <Activity className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-emerald-700 mt-1">5.0%</div>
          <div className="text-xs text-gray-400 mt-1">行业同类中位数 3.2%</div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">平均笔单价 (AOV)</span>
            <div className="p-2 bg-amber-50 text-amber-600 rounded-lg">
              <ShoppingBag className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-amber-700 mt-1">¥248.50</div>
          <div className="flex items-center gap-1 text-xs text-emerald-600 mt-1 font-semibold">
            <ArrowUpRight className="w-3.5 h-3.5" /> 连带件数 2.4 件/单
          </div>
        </div>
      </div>

      {/* Main Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Traffic & GMV Hourly Trend (2 cols) */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-sm font-bold text-gray-900">24小时实时流量与GMV走势 (Hourly Trend)</h3>
              <p className="text-xs text-gray-400">实时反映波峰波谷与高峰购买转化时段</p>
            </div>
            <div className="flex items-center gap-4 text-xs font-medium text-gray-600">
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-blue-600" /> GMV 销售额
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-purple-500" /> UV 访客数
              </span>
            </div>
          </div>

          <div className="h-[280px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trafficHourly}>
                <defs>
                  <linearGradient id="gmvGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2563EB" stopOpacity={0.25} />
                    <stop offset="95%" stopColor="#2563EB" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="uvGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#8B5CF6" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#8B5CF6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#F1F5F9" />
                <XAxis dataKey="time" stroke="#94A3B8" fontSize={11} tickLine={false} />
                <YAxis stroke="#94A3B8" fontSize={11} tickLine={false} axisLine={false} />
                <Tooltip
                  formatter={(value: any, name: any) => {
                    if (name === 'gmv') return [`¥${value.toLocaleString()}`, '销售GMV'];
                    if (name === 'uv') return [`${value.toLocaleString()} 人`, 'UV访客'];
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
                <Area type="monotone" dataKey="gmv" stroke="#2563EB" strokeWidth={2} fillOpacity={1} fill="url(#gmvGradient)" />
                <Area type="monotone" dataKey="uv" stroke="#8B5CF6" strokeWidth={2} fillOpacity={1} fill="url(#uvGradient)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Channel Share Pie Chart (1 col) */}
        <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-gray-900">全渠道客流来源占比 (Channels)</h3>
            <p className="text-xs text-gray-400">微信生态与移动端贡献主要客流</p>
          </div>

          <div className="h-[200px] w-full flex items-center justify-center my-2">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={channelShare}
                  cx="50%"
                  cy="50%"
                  innerRadius={50}
                  outerRadius={75}
                  paddingAngle={4}
                  dataKey="value"
                >
                  {channelShare.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => [`${value}%`, '占比']} />
              </PieChart>
            </ResponsiveContainer>
          </div>

          <div className="space-y-1.5 text-xs">
            {channelShare.map((item) => (
              <div key={item.name} className="flex items-center justify-between text-gray-600">
                <span className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                  {item.name}
                </span>
                <span className="font-bold text-gray-900">{item.value}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Conversion Funnel */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5">
        <h3 className="text-sm font-bold text-gray-900 mb-1">全链路用户交易转化漏斗 (Conversion Funnel)</h3>
        <p className="text-xs text-gray-400 mb-4">从公域浏览至最终付款的逐层流失与转化效能</p>

        <div className="space-y-3">
          {funnelData.map((stage, idx) => (
            <div key={stage.stage} className="relative">
              <div className="flex items-center justify-between text-xs font-semibold text-gray-800 mb-1">
                <span>{stage.stage}</span>
                <span className="text-blue-600">{stage.count} 人 ({stage.rate})</span>
              </div>
              <div className="w-full bg-gray-100 h-3 rounded-full overflow-hidden">
                <div
                  className="h-3 rounded-full bg-gradient-to-r from-blue-600 to-indigo-600"
                  style={{ width: stage.rate }}
                />
              </div>
            </div>
          ))}
        </div>
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
