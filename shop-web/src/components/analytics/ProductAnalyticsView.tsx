import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { 
  BarChart3, 
  TrendingUp, 
  TrendingDown, 
  Award, 
  Search, 
  Download, 
  Package, 
  Percent, 
  RefreshCw, 
  Layers 
} from 'lucide-react';
import { 
  BarChart, 
  Bar, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer 
} from 'recharts';

interface ProductMetric {
  id: string;
  name: string;
  category: string;
  salesVolume: number; // 销量(件)
  gmv: number; // 销售额(¥)
  repurchaseRate: number; // 复购率%
  turnoverDays: number; // 周转天数
  profitMargin: number; // 毛利率%
  trend: 'up' | 'down' | 'steady';
}

const productMetrics: ProductMetric[] = [
  {
    id: 'p-1',
    name: '极客降噪无线蓝牙耳机 Pro Max',
    category: '电子数码',
    salesVolume: 2840,
    gmv: 3689160,
    repurchaseRate: 24.5,
    turnoverDays: 14,
    profitMargin: 53.8,
    trend: 'up'
  },
  {
    id: 'p-2',
    name: '智能磁吸无线快充底座 3合1',
    category: '电子数码',
    salesVolume: 1980,
    gmv: 592020,
    repurchaseRate: 31.2,
    turnoverDays: 18,
    profitMargin: 57.2,
    trend: 'up'
  },
  {
    id: 'p-3',
    name: '太空慢回弹记忆棉护颈深睡枕',
    category: '居家生活',
    salesVolume: 1420,
    gmv: 282580,
    repurchaseRate: 18.0,
    turnoverDays: 22,
    profitMargin: 60.3,
    trend: 'up'
  },
  {
    id: 'p-4',
    name: '天然有机大马士革玫瑰纯露 200ml',
    category: '美妆个护',
    salesVolume: 1250,
    gmv: 210000,
    repurchaseRate: 46.8,
    turnoverDays: 12,
    profitMargin: 71.4,
    trend: 'up'
  },
  {
    id: 'p-5',
    name: '重磅纯棉宽松纯色水洗T恤',
    category: '服装鞋包',
    salesVolume: 980,
    gmv: 126420,
    repurchaseRate: 28.3,
    turnoverDays: 35,
    profitMargin: 45.7,
    trend: 'down'
  }
];

export const ProductAnalyticsView: React.FC = () => {
  const { showToast } = useAdmin();
  const [categoryFilter, setCategoryFilter] = useState('all');

  const filtered = productMetrics.filter((p) =>
    categoryFilter === 'all' ? true : p.category === categoryFilter
  );

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              商品排行与动销复购分析 (Product BI & Repurchase)
            </h2>
            <span className="text-xs bg-purple-50 text-purple-700 font-semibold px-2 py-0.5 rounded-full border border-purple-200">
              品类洞察
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            单品销售排行榜单、品类动销率、库存周转效率与高复购核心爆品矩阵。
          </p>
        </div>

        <button
          onClick={() => showToast('商品动销深度剖析报告已导出为 Excel', 'success')}
          className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs"
        >
          <Download className="w-4 h-4" />
          <span>导出商品动销分析</span>
        </button>
      </div>

      {/* Top Chart */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5">
        <h3 className="text-sm font-bold text-gray-900 mb-1">畅销榜单 Top 5 销售额对比 (GMV Ranking)</h3>
        <p className="text-xs text-gray-400 mb-4">柱状直观对比核心主力单品的营收贡献</p>

        <div className="h-[240px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={productMetrics} layout="vertical">
              <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#F1F5F9" />
              <XAxis type="number" stroke="#94A3B8" fontSize={11} tickLine={false} />
              <YAxis dataKey="name" type="category" stroke="#94A3B8" fontSize={11} width={160} tickLine={false} />
              <Tooltip
                formatter={(val: any) => [`¥${Number(val).toLocaleString()}`, '销售GMV']}
                contentStyle={{ borderRadius: '8px', fontSize: '12px' }}
              />
              <Bar dataKey="gmv" fill="#2563EB" radius={[0, 4, 4, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50 flex items-center justify-between">
          <span className="text-xs font-bold text-gray-700">单品全生命周期动销指标</span>
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="h-[32px] px-2.5 text-xs rounded-lg border border-gray-300 bg-white"
          >
            <option value="all">全新品类</option>
            <option value="电子数码">电子数码</option>
            <option value="居家生活">居家生活</option>
            <option value="美妆个护">美妆个护</option>
            <option value="服装鞋包">服装鞋包</option>
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase">
              <tr>
                <th className="py-3 px-4">排名 / 商品名称</th>
                <th className="py-3 px-4">所属品类</th>
                <th className="py-3 px-4 text-right">销量 (件)</th>
                <th className="py-3 px-4 text-right">累计 GMV (¥)</th>
                <th className="py-3 px-4 text-right">老客复购率</th>
                <th className="py-3 px-4 text-right">毛利率</th>
                <th className="py-3 px-4 text-right">库存周转天数</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-sm">
              {filtered.map((item, idx) => (
                <tr key={item.id} className="hover:bg-[#F8FAFC]">
                  <td className="py-3 px-4">
                    <div className="flex items-center gap-2">
                      <span className={`w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold ${
                        idx === 0 ? 'bg-amber-100 text-amber-800' : idx === 1 ? 'bg-gray-200 text-gray-700' : 'bg-gray-100 text-gray-600'
                      }`}>
                        {idx + 1}
                      </span>
                      <span className="font-bold text-gray-900 text-xs sm:text-sm">{item.name}</span>
                    </div>
                  </td>

                  <td className="py-3 px-4 text-xs text-gray-600">{item.category}</td>

                  <td className="py-3 px-4 text-right font-mono font-bold text-gray-800">
                    {item.salesVolume.toLocaleString()}
                  </td>

                  <td className="py-3 px-4 text-right font-mono font-bold text-blue-600">
                    ¥{item.gmv.toLocaleString()}
                  </td>

                  <td className="py-3 px-4 text-right font-mono font-bold text-purple-700">
                    {item.repurchaseRate}%
                  </td>

                  <td className="py-3 px-4 text-right font-mono font-bold text-emerald-700">
                    {item.profitMargin}%
                  </td>

                  <td className="py-3 px-4 text-right font-mono text-gray-600 text-xs">
                    {item.turnoverDays} 天
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
