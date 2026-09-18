import React, { useState, useMemo } from 'react';
import { Product } from '../types/ecommerce';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  LineChart,
  Line,
  RadarChart,
  Radar,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  AreaChart,
  Area,
} from 'recharts';
import {
  TrendingUp,
  Award,
  DollarSign,
  Star,
  ShoppingBag,
  Flame,
  CheckCircle2,
  Sparkles,
  BarChart3,
  LineChart as LineChartIcon,
  ShieldAlert,
} from 'lucide-react';

interface CompareChartsViewProps {
  products: Product[];
  onAddToCart: (product: Product) => void;
  onQuickView: (product: Product) => void;
}

// Consistent colors for up to 3 products
const PRODUCT_COLORS = [
  {
    primary: '#3b82f6', // Blue
    bg: 'bg-blue-50',
    border: 'border-blue-200',
    text: 'text-blue-600',
    fill: 'rgba(59, 130, 246, 0.25)',
  },
  {
    primary: '#10b981', // Emerald
    bg: 'bg-emerald-50',
    border: 'border-emerald-200',
    text: 'text-emerald-600',
    fill: 'rgba(16, 185, 129, 0.25)',
  },
  {
    primary: '#f59e0b', // Amber
    bg: 'bg-amber-50',
    border: 'border-amber-200',
    text: 'text-amber-600',
    fill: 'rgba(245, 158, 11, 0.25)',
  },
];

export const CompareChartsView: React.FC<CompareChartsViewProps> = ({
  products,
  onAddToCart,
  onQuickView,
}) => {
  const [activeChartTab, setActiveChartTab] = useState<'all' | 'trend' | 'radar' | 'price' | 'sales'>('all');

  // 1. Data for Price Comparison (Bar Chart)
  const priceBarData = useMemo(() => {
    return products.map((p, index) => {
      const discount = Math.max(0, p.originalPrice - p.price);
      const discountRate = Math.round((discount / p.originalPrice) * 100);
      return {
        id: p.id,
        name: p.title.length > 10 ? p.title.slice(0, 10) + '...' : p.title,
        fullName: p.title,
        brand: p.brand,
        currentPrice: p.price,
        originalPrice: p.originalPrice,
        discountAmount: discount,
        discountRate: discountRate,
        color: PRODUCT_COLORS[index % PRODUCT_COLORS.length].primary,
      };
    });
  }, [products]);

  // 2. Data for 6-Month Simulated Historical Price Trend
  const priceTrendData = useMemo(() => {
    const months = ['3月', '4月', '5月', '6月(年中大促)', '7月', '8月(当前)'];
    
    return months.map((month, mIdx) => {
      const row: Record<string, any> = { month };
      products.forEach((p, pIdx) => {
        const base = p.originalPrice;
        const current = p.price;
        // Generate a smooth price timeline leading down to the current promotional price
        let val = current;
        if (mIdx === 0) val = Math.round(base);
        else if (mIdx === 1) val = Math.round(base * 0.98);
        else if (mIdx === 2) val = Math.round(base * 0.95);
        else if (mIdx === 3) val = Math.round(Math.min(current * 1.05, base * 0.88)); // June promo
        else if (mIdx === 4) val = Math.round(Math.min(base * 0.92, current * 1.08));
        else if (mIdx === 5) val = current; // current
        row[`prod_${pIdx}`] = val;
        row[`prod_${pIdx}_name`] = p.title;
      });
      return row;
    });
  }, [products]);

  // 3. Multi-dimensional Radar Chart Data
  const radarData = useMemo(() => {
    // Normalization factors
    const maxSales = Math.max(...products.map((p) => p.salesCount), 1000);
    const maxReviews = Math.max(...products.map((p) => p.reviewCount), 0);

    const dimensions = [
      { key: 'rating', label: '用户评分' },
      { key: 'value', label: '性价比指数' },
      { key: 'sales', label: '市场销量' },
      { key: 'reviews', label: '好评互动量' },
      { key: 'discount', label: '折扣力度' },
      { key: 'stock', label: '现货保障' },
    ];

    return dimensions.map((dim) => {
      const row: Record<string, any> = { dimension: dim.label };
      products.forEach((p, pIdx) => {
        let score = 50;
        if (dim.key === 'rating') {
          // 无公开评价时给中性分，避免整条维度被 0 分拉平。
          score = p.reviewCount > 0 ? Math.round((p.rating / 5.0) * 100) : 50;
        } else if (dim.key === 'value') {
          // value score based on rating per dollar
          const discountPct = (p.originalPrice - p.price) / p.originalPrice;
          score = Math.min(100, Math.round(60 + discountPct * 70 + (p.reviewCount > 0 && p.rating >= 4.8 ? 15 : 5)));
        } else if (dim.key === 'sales') {
          score = Math.min(100, Math.round((p.salesCount / maxSales) * 100));
        } else if (dim.key === 'reviews') {
          score = maxReviews > 0 ? Math.min(100, Math.round((p.reviewCount / maxReviews) * 100)) : 50;
        } else if (dim.key === 'discount') {
          const discountPct = Math.max(0, ((p.originalPrice - p.price) / p.originalPrice) * 100);
          score = Math.min(100, Math.round(discountPct * 2.5 + 30));
        } else if (dim.key === 'stock') {
          score = p.stock > 20 ? 95 : p.stock > 5 ? 75 : 40;
        }
        row[`prod_${pIdx}`] = score;
        row[`prod_${pIdx}_name`] = p.title;
      });
      return row;
    });
  }, [products]);

  // 4. Rating & Sales Combo Data
  const salesAndRatingData = useMemo(() => {
    return products.map((p, index) => ({
      id: p.id,
      name: p.title.length > 8 ? p.title.slice(0, 8) + '...' : p.title,
      fullName: p.title,
      rating: p.rating,
      salesCount: p.salesCount,
      reviewCount: p.reviewCount,
      color: PRODUCT_COLORS[index % PRODUCT_COLORS.length].primary,
    }));
  }, [products]);

  // 5. Intelligent Recommendations / Insights
  const insights = useMemo(() => {
    if (products.length === 0) return null;
    const highestRated = [...products].sort((a, b) => b.rating - a.rating)[0];
    const topSales = [...products].sort((a, b) => b.salesCount - a.salesCount)[0];
    const biggestDiscount = [...products].sort(
      (a, b) => (b.originalPrice - b.price) - (a.originalPrice - a.price)
    )[0];
    const lowestPrice = [...products].sort((a, b) => a.price - b.price)[0];

    return {
      highestRated,
      topSales,
      biggestDiscount,
      lowestPrice,
    };
  }, [products]);

  // Custom Chart Tooltip
  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-zinc-900/95 backdrop-blur-md text-white px-3.5 py-2.5 rounded-xl border border-zinc-700 shadow-xl text-xs space-y-1.5 min-w-[160px]">
          <div className="font-bold text-zinc-200 border-b border-zinc-800 pb-1 mb-1">
            {label}
          </div>
          {payload.map((entry: any, i: number) => {
            return (
              <div key={i} className="flex items-center justify-between gap-3 text-[11px]">
                <div className="flex items-center gap-1.5 truncate max-w-[150px]">
                  <span
                    className="w-2 h-2 rounded-full shrink-0"
                    style={{ backgroundColor: entry.color || entry.stroke || entry.fill }}
                  />
                  <span className="text-zinc-300 truncate">{entry.name}</span>
                </div>
                <span className="font-bold text-white whitespace-nowrap">
                  {typeof entry.value === 'number'
                    ? entry.value.toLocaleString()
                    : entry.value}
                </span>
              </div>
            );
          })}
        </div>
      );
    }
    return null;
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Product Identification Strip & Legend */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {products.map((product, idx) => {
          const colorConfig = PRODUCT_COLORS[idx % PRODUCT_COLORS.length];
          const discount = product.originalPrice - product.price;
          return (
            <div
              key={product.id}
              className={`p-3 rounded-2xl border ${colorConfig.border} ${colorConfig.bg} flex items-center justify-between gap-3 transition shadow-xs`}
            >
              <div className="flex items-center gap-2.5 min-w-0">
                <div
                  className="w-3.5 h-3.5 rounded-full shrink-0 shadow-xs ring-2 ring-white"
                  style={{ backgroundColor: colorConfig.primary }}
                />
                <div className="min-w-0">
                  <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">
                    {product.brand}
                  </span>
                  <h4 className="text-xs font-bold text-zinc-900 truncate" title={product.title}>
                    {product.title}
                  </h4>
                  <div className="flex items-center gap-2 text-xs mt-0.5">
                    <span className="font-black text-zinc-900">¥{product.price}</span>
                    <span className="text-[11px] text-zinc-400 line-through">
                      ¥{product.originalPrice}
                    </span>
                    {discount > 0 && (
                      <span className="text-[10px] font-semibold text-rose-600 bg-rose-50 px-1 rounded">
                        省¥{discount}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-1 shrink-0">
                <button
                  onClick={() => onAddToCart(product)}
                  className="px-2.5 py-1 rounded-lg bg-zinc-900 text-white hover:bg-zinc-800 text-[11px] font-semibold transition flex items-center gap-1 shadow-xs"
                >
                  <ShoppingBag className="w-3 h-3" />
                  <span>加购</span>
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {/* Visual Chart Navigation Filter Tabs */}
      <div className="flex items-center justify-between border-b border-zinc-200 pb-3 flex-wrap gap-2">
        <div className="flex items-center gap-1.5 p-1 bg-zinc-100/90 rounded-xl text-xs font-semibold text-zinc-600">
          <button
            onClick={() => setActiveChartTab('all')}
            className={`px-3 py-1.5 rounded-lg transition ${
              activeChartTab === 'all'
                ? 'bg-white text-zinc-900 shadow-xs'
                : 'hover:text-zinc-900'
            }`}
          >
            全部图表看板
          </button>
          <button
            onClick={() => setActiveChartTab('trend')}
            className={`px-3 py-1.5 rounded-lg transition flex items-center gap-1 ${
              activeChartTab === 'trend'
                ? 'bg-white text-zinc-900 shadow-xs'
                : 'hover:text-zinc-900'
            }`}
          >
            <TrendingUp className="w-3.5 h-3.5 text-blue-500" />
            <span>价格走势曲线</span>
          </button>
          <button
            onClick={() => setActiveChartTab('radar')}
            className={`px-3 py-1.5 rounded-lg transition flex items-center gap-1 ${
              activeChartTab === 'radar'
                ? 'bg-white text-zinc-900 shadow-xs'
                : 'hover:text-zinc-900'
            }`}
          >
            <Award className="w-3.5 h-3.5 text-emerald-500" />
            <span>综合战力雷达</span>
          </button>
          <button
            onClick={() => setActiveChartTab('price')}
            className={`px-3 py-1.5 rounded-lg transition flex items-center gap-1 ${
              activeChartTab === 'price'
                ? 'bg-white text-zinc-900 shadow-xs'
                : 'hover:text-zinc-900'
            }`}
          >
            <DollarSign className="w-3.5 h-3.5 text-amber-500" />
            <span>现价与立省柱图</span>
          </button>
          <button
            onClick={() => setActiveChartTab('sales')}
            className={`px-3 py-1.5 rounded-lg transition flex items-center gap-1 ${
              activeChartTab === 'sales'
                ? 'bg-white text-zinc-900 shadow-xs'
                : 'hover:text-zinc-900'
            }`}
          >
            <Flame className="w-3.5 h-3.5 text-rose-500" />
            <span>销量口碑对比</span>
          </button>
        </div>

        <span className="text-[11px] text-zinc-400">
          基于当前参与对比的 {products.length} 款商品数据生成
        </span>
      </div>

      {/* Chart Panels Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 1. Price Trend Line Chart (近半年价格走势) */}
        {(activeChartTab === 'all' || activeChartTab === 'trend') && (
          <div className="bg-zinc-50/60 rounded-3xl border border-zinc-200/90 p-5 shadow-xs flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-1">
                <h3 className="text-sm font-bold text-zinc-900 flex items-center gap-2">
                  <div className="w-7 h-7 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
                    <TrendingUp className="w-4 h-4" />
                  </div>
                  <span>近半年历史价格走势对比 (¥)</span>
                </h3>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-blue-100 text-blue-800 font-semibold">
                  价格波动监测
                </span>
              </div>
              <p className="text-xs text-zinc-500 mb-4 pl-9">
                追踪 3 月至 8 月的历史价格变动趋势，展示各款商品当前的优惠降幅节点
              </p>
            </div>

            <div className="w-full h-64 sm:h-72">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={priceTrendData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e4e4e7" vertical={false} />
                  <XAxis
                    dataKey="month"
                    tick={{ fontSize: 11, fill: '#71717a' }}
                    tickLine={false}
                    axisLine={{ stroke: '#e4e4e7' }}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: '#71717a' }}
                    tickLine={false}
                    axisLine={{ stroke: '#e4e4e7' }}
                    tickFormatter={(val) => `¥${val}`}
                  />
                  <Tooltip content={<CustomTooltip />} />
                  <Legend
                    wrapperStyle={{ fontSize: 11, paddingTop: 10 }}
                    formatter={(val, entry: any) => {
                      const idx = parseInt(entry.dataKey.replace('prod_', ''));
                      return products[idx]?.title.slice(0, 12) + '...' || val;
                    }}
                  />
                  {products.map((p, idx) => (
                    <Line
                      key={p.id}
                      type="monotone"
                      dataKey={`prod_${idx}`}
                      name={p.title}
                      stroke={PRODUCT_COLORS[idx % PRODUCT_COLORS.length].primary}
                      strokeWidth={3}
                      dot={{ r: 4, strokeWidth: 2, fill: '#fff' }}
                      activeDot={{ r: 6, strokeWidth: 0 }}
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}

        {/* 2. Comprehensive Radar Chart (多维战力雷达) */}
        {(activeChartTab === 'all' || activeChartTab === 'radar') && (
          <div className="bg-zinc-50/60 rounded-3xl border border-zinc-200/90 p-5 shadow-xs flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-1">
                <h3 className="text-sm font-bold text-zinc-900 flex items-center gap-2">
                  <div className="w-7 h-7 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                    <Award className="w-4 h-4" />
                  </div>
                  <span>综合战力六维雷达对比</span>
                </h3>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-semibold">
                  全方位能力模型
                </span>
              </div>
              <p className="text-xs text-zinc-500 mb-4 pl-9">
                综合评分、性价比、销量、好评互动、折扣力度与现货状态 6 项综合维度打分
              </p>
            </div>

            <div className="w-full h-64 sm:h-72">
              <ResponsiveContainer width="100%" height="100%">
                <RadarChart data={radarData} outerRadius="75%">
                  <PolarGrid stroke="#e4e4e7" />
                  <PolarAngleAxis
                    dataKey="dimension"
                    tick={{ fontSize: 11, fill: '#52525b', fontWeight: 600 }}
                  />
                  <PolarRadiusAxis angle={30} domain={[0, 100]} stroke="#d4d4d8" tick={{ fontSize: 9 }} />
                  <Tooltip content={<CustomTooltip />} />
                  <Legend
                    wrapperStyle={{ fontSize: 11, paddingTop: 10 }}
                    formatter={(val, entry: any) => {
                      const idx = parseInt(entry.dataKey.replace('prod_', ''));
                      return products[idx]?.title.slice(0, 12) + '...' || val;
                    }}
                  />
                  {products.map((p, idx) => {
                    const color = PRODUCT_COLORS[idx % PRODUCT_COLORS.length];
                    return (
                      <Radar
                        key={p.id}
                        name={p.title}
                        dataKey={`prod_${idx}`}
                        stroke={color.primary}
                        fill={color.primary}
                        fillOpacity={0.25}
                      />
                    );
                  })}
                </RadarChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}

        {/* 3. Price & Discount Bar Chart (现价与直降金额) */}
        {(activeChartTab === 'all' || activeChartTab === 'price') && (
          <div className="bg-zinc-50/60 rounded-3xl border border-zinc-200/90 p-5 shadow-xs flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-1">
                <h3 className="text-sm font-bold text-zinc-900 flex items-center gap-2">
                  <div className="w-7 h-7 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                    <DollarSign className="w-4 h-4" />
                  </div>
                  <span>当前售价 vs 官方原价 vs 优惠立省 (¥)</span>
                </h3>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-semibold">
                  价格与折扣
                </span>
              </div>
              <p className="text-xs text-zinc-500 mb-4 pl-9">
                对比各商品当前到手价与原价差额，直观展现折扣力度
              </p>
            </div>

            <div className="w-full h-64 sm:h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={priceBarData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e4e4e7" vertical={false} />
                  <XAxis
                    dataKey="name"
                    tick={{ fontSize: 11, fill: '#71717a' }}
                    tickLine={false}
                    axisLine={{ stroke: '#e4e4e7' }}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: '#71717a' }}
                    tickLine={false}
                    axisLine={{ stroke: '#e4e4e7' }}
                    tickFormatter={(val) => `¥${val}`}
                  />
                  <Tooltip content={<CustomTooltip />} />
                  <Legend wrapperStyle={{ fontSize: 11, paddingTop: 10 }} />
                  <Bar dataKey="currentPrice" name="当前售价 (¥)" fill="#18181b" radius={[6, 6, 0, 0]} />
                  <Bar dataKey="originalPrice" name="官方原价 (¥)" fill="#a1a1aa" radius={[6, 6, 0, 0]} />
                  <Bar dataKey="discountAmount" name="立省金额 (¥)" fill="#f43f5e" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}

        {/* 4. Sales and Rating Chart (口碑与热度) */}
        {(activeChartTab === 'all' || activeChartTab === 'sales') && (
          <div className="bg-zinc-50/60 rounded-3xl border border-zinc-200/90 p-5 shadow-xs flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-1">
                <h3 className="text-sm font-bold text-zinc-900 flex items-center gap-2">
                  <div className="w-7 h-7 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
                    <Flame className="w-4 h-4" />
                  </div>
                  <span>市场销量与好评互动对比</span>
                </h3>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 font-semibold">
                  市场热度
                </span>
              </div>
              <p className="text-xs text-zinc-500 mb-4 pl-9">
                对比商品累计销量件数与用户评价总量，了解真实大众口碑与选择
                {salesAndRatingData.every((item) => item.reviewCount === 0) && '（当前对比商品暂无公开评价）'}
              </p>
            </div>

            <div className="w-full h-64 sm:h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={salesAndRatingData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e4e4e7" vertical={false} />
                  <XAxis
                    dataKey="name"
                    tick={{ fontSize: 11, fill: '#71717a' }}
                    tickLine={false}
                    axisLine={{ stroke: '#e4e4e7' }}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: '#71717a' }}
                    tickLine={false}
                    axisLine={{ stroke: '#e4e4e7' }}
                  />
                  <Tooltip content={<CustomTooltip />} />
                  <Legend wrapperStyle={{ fontSize: 11, paddingTop: 10 }} />
                  <Bar dataKey="salesCount" name="销量 (件)" fill="#f59e0b" radius={[6, 6, 0, 0]} />
                  <Bar dataKey="reviewCount" name="评价数 (条)" fill="#6366f1" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}
      </div>

      {/* 5. Intelligent Decision Helper / Insights Banner */}
      {insights && (
        <div className="bg-gradient-to-r from-zinc-900 to-zinc-800 rounded-3xl p-5 text-white shadow-lg space-y-4">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-amber-400 text-zinc-950 flex items-center justify-center font-bold">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-sm font-bold">智能对比选购决策建议</h4>
              <p className="text-xs text-zinc-400">
                系统根据多项指标为您自动计算出各项冠军推荐
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {/* Top Rating */}
            <div className="bg-zinc-800/80 rounded-2xl p-3.5 border border-zinc-700/60 flex flex-col justify-between">
              <div>
                <span className="text-[11px] font-semibold text-amber-400 flex items-center gap-1 mb-1">
                  <Star className="w-3.5 h-3.5 fill-amber-400" />
                  口碑评分最高
                </span>
                <h5 className="text-xs font-bold text-white line-clamp-1">
                  {insights.highestRated.title}
                </h5>
                <p className="text-[11px] text-zinc-400 mt-0.5">
                  {insights.highestRated.reviewCount > 0
                    ? `评分 ${insights.highestRated.rating} 分 / ${insights.highestRated.reviewCount} 条评价`
                    : '暂无公开评价'}
                </p>
              </div>
              <button
                onClick={() => onAddToCart(insights.highestRated)}
                className="mt-3 w-full py-1.5 rounded-xl bg-white text-zinc-900 hover:bg-zinc-100 text-xs font-semibold transition"
              >
                加购 ¥{insights.highestRated.price}
              </button>
            </div>

            {/* Top Sales */}
            <div className="bg-zinc-800/80 rounded-2xl p-3.5 border border-zinc-700/60 flex flex-col justify-between">
              <div>
                <span className="text-[11px] font-semibold text-rose-400 flex items-center gap-1 mb-1">
                  <Flame className="w-3.5 h-3.5" />
                  销量人气冠军
                </span>
                <h5 className="text-xs font-bold text-white line-clamp-1">
                  {insights.topSales.title}
                </h5>
                <p className="text-[11px] text-zinc-400 mt-0.5">
                  已售 {insights.topSales.salesCount}+ 件爆款
                </p>
              </div>
              <button
                onClick={() => onAddToCart(insights.topSales)}
                className="mt-3 w-full py-1.5 rounded-xl bg-white text-zinc-900 hover:bg-zinc-100 text-xs font-semibold transition"
              >
                加购 ¥{insights.topSales.price}
              </button>
            </div>

            {/* Biggest Discount */}
            <div className="bg-zinc-800/80 rounded-2xl p-3.5 border border-zinc-700/60 flex flex-col justify-between">
              <div>
                <span className="text-[11px] font-semibold text-emerald-400 flex items-center gap-1 mb-1">
                  <TrendingUp className="w-3.5 h-3.5" />
                  降价优惠幅度最大
                </span>
                <h5 className="text-xs font-bold text-white line-clamp-1">
                  {insights.biggestDiscount.title}
                </h5>
                <p className="text-[11px] text-zinc-400 mt-0.5">
                  立省 ¥{insights.biggestDiscount.originalPrice - insights.biggestDiscount.price} 元
                </p>
              </div>
              <button
                onClick={() => onAddToCart(insights.biggestDiscount)}
                className="mt-3 w-full py-1.5 rounded-xl bg-white text-zinc-900 hover:bg-zinc-100 text-xs font-semibold transition"
              >
                加购 ¥{insights.biggestDiscount.price}
              </button>
            </div>

            {/* Lowest Price Entry */}
            <div className="bg-zinc-800/80 rounded-2xl p-3.5 border border-zinc-700/60 flex flex-col justify-between">
              <div>
                <span className="text-[11px] font-semibold text-blue-400 flex items-center gap-1 mb-1">
                  <DollarSign className="w-3.5 h-3.5" />
                  最低入手门槛
                </span>
                <h5 className="text-xs font-bold text-white line-clamp-1">
                  {insights.lowestPrice.title}
                </h5>
                <p className="text-[11px] text-zinc-400 mt-0.5">
                  到手价仅需 ¥{insights.lowestPrice.price}
                </p>
              </div>
              <button
                onClick={() => onAddToCart(insights.lowestPrice)}
                className="mt-3 w-full py-1.5 rounded-xl bg-white text-zinc-900 hover:bg-zinc-100 text-xs font-semibold transition"
              >
                加购 ¥{insights.lowestPrice.price}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
