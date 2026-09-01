import React, { useEffect, useMemo, useState } from 'react';
import { BarChart3, Download, RefreshCw, Search } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { BackendReportingProductRankingItem, downloadReportingExport, getReportingProductRanking } from '../../api/adminApi';
import { useAdmin } from '../../context/AdminContext';

function formatDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function money(value: number): string {
  return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export const ProductAnalyticsView: React.FC = () => {
  const { showToast } = useAdmin();
  const [rangeDays, setRangeDays] = useState<7 | 30>(30);
  const [rows, setRows] = useState<BackendReportingProductRankingItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [categoryFilter, setCategoryFilter] = useState('all');

  const loadRanking = async () => {
    setLoading(true);
    setError(null);
    const endDate = new Date();
    const startDate = new Date(endDate);
    startDate.setDate(startDate.getDate() - rangeDays + 1);
    try {
      const result = await getReportingProductRanking({ startDate: formatDate(startDate), endDate: formatDate(endDate), limit: 20 });
      setRows(Array.isArray(result) ? result : []);
    } catch (requestError) {
      setRows([]);
      setError(requestError instanceof Error ? requestError.message : '商品排行加载失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void loadRanking(); }, [rangeDays]);

  const categories = useMemo(() => Array.from(new Set(rows.map((item) => item.categoryName || '未分类'))), [rows]);
  const filtered = categoryFilter === 'all' ? rows : rows.filter((item) => (item.categoryName || '未分类') === categoryFilter);

  const exportReport = async () => {
    setExporting(true);
    const endDate = new Date();
    const startDate = new Date(endDate);
    startDate.setDate(startDate.getDate() - rangeDays + 1);
    try {
      const blob = await downloadReportingExport({ reportType: 'PRODUCT_RANKING', startDate: formatDate(startDate), endDate: formatDate(endDate), limit: 100 });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `商品动销排行-${formatDate(endDate)}.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
      showToast('商品动销排行已导出', 'success');
    } catch (requestError) {
      showToast(requestError instanceof Error ? requestError.message : '导出失败，请稍后重试', 'error');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">商品销售排行与动销分析</h2>
            <span className="text-xs bg-purple-50 text-purple-700 font-semibold px-2 py-0.5 rounded-full border border-purple-200">实时聚合</span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">按已支付订单统计销量、销售额和订单贡献，帮助快速识别主力商品。</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1 bg-white p-1 rounded-lg border border-gray-200 shadow-2xs" aria-label="统计周期">
            {[7, 30].map((days) => (
              <button key={days} type="button" onClick={() => setRangeDays(days as 7 | 30)} className={`px-3 py-1 text-xs font-semibold rounded-md transition-colors ${rangeDays === days ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'}`}>近 {days} 天</button>
            ))}
          </div>
          <button type="button" onClick={() => void exportReport()} disabled={exporting || loading} className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs">
            {exporting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            <span>{exporting ? '导出中…' : '导出 CSV'}</span>
          </button>
        </div>
      </div>

      {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 flex items-center justify-between gap-3" role="alert"><span className="text-sm text-red-700">{error}</span><button type="button" onClick={() => void loadRanking()} className="text-xs font-semibold text-red-700 hover:text-red-900 inline-flex items-center gap-1"><RefreshCw className="w-3.5 h-3.5" /> 重试</button></div>}

      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5">
        <div className="flex items-center justify-between mb-4"><div><h3 className="text-sm font-bold text-gray-900">销售额 Top 5</h3><p className="text-xs text-gray-400">统计周期内有效支付订单的商品销售额</p></div><BarChart3 className="w-5 h-5 text-blue-600" /></div>
        {loading ? <div className="h-[240px] flex items-center justify-center text-sm text-gray-400" role="status">正在加载商品排行…</div> : filtered.length === 0 ? <div className="h-[240px] flex flex-col items-center justify-center text-sm text-gray-400"><Search className="w-6 h-6 mb-2 text-gray-300" />暂无符合条件的销售数据</div> : <div className="h-[240px] w-full"><ResponsiveContainer width="100%" height="100%"><BarChart data={filtered.slice(0, 5)} layout="vertical" margin={{ left: 8, right: 16 }}><CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#F1F5F9" /><XAxis type="number" stroke="#94A3B8" fontSize={11} tickLine={false} /><YAxis dataKey="productName" type="category" stroke="#94A3B8" fontSize={11} width={160} tickLine={false} /><Tooltip formatter={(value: number) => [money(value), '销售额']} contentStyle={{ borderRadius: '8px', fontSize: '12px' }} /><Bar dataKey="salesAmount" fill="#2563EB" radius={[0, 4, 4, 0]} /></BarChart></ResponsiveContainer></div>}
      </div>

      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50 flex items-center justify-between"><span className="text-xs font-bold text-gray-700">商品销售明细</span><select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)} className="h-[32px] px-2.5 text-xs rounded-lg border border-gray-300 bg-white" aria-label="按类目筛选"><option value="all">全部类目</option>{categories.map((category) => <option key={category} value={category}>{category}</option>)}</select></div>
        <div className="overflow-x-auto"><table className="w-full text-left border-collapse"><thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase"><tr><th className="py-3 px-4">排名 / 商品名称</th><th className="py-3 px-4">所属类目</th><th className="py-3 px-4 text-right">销量（件）</th><th className="py-3 px-4 text-right">销售额</th><th className="py-3 px-4 text-right">订单数</th></tr></thead><tbody className="divide-y divide-gray-100 text-sm">{loading ? <tr><td colSpan={5} className="py-10 text-center text-sm text-gray-400" role="status">正在加载商品排行…</td></tr> : filtered.length === 0 ? <tr><td colSpan={5} className="py-10 text-center text-sm text-gray-400">暂无销售数据，调整周期或类目后再试</td></tr> : filtered.map((item) => <tr key={`${item.productId || item.productName}-${item.rank}`} className="hover:bg-[#F8FAFC]"><td className="py-3 px-4"><div className="flex items-center gap-2"><span className="w-5 h-5 rounded-full bg-blue-50 text-blue-700 flex items-center justify-center text-xs font-bold">{item.rank}</span><span className="font-bold text-gray-900 text-xs sm:text-sm">{item.productName}</span></div></td><td className="py-3 px-4 text-xs text-gray-600">{item.categoryName || '未分类'}</td><td className="py-3 px-4 text-right font-mono font-bold text-gray-800">{Number(item.salesVolume || 0).toLocaleString()}</td><td className="py-3 px-4 text-right font-mono font-bold text-blue-600">{money(item.salesAmount)}</td><td className="py-3 px-4 text-right font-mono text-gray-600">{Number(item.orderCount || 0).toLocaleString()}</td></tr>)}</tbody></table></div>
      </div>
    </div>
  );
};
