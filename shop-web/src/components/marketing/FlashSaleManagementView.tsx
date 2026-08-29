import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { 
  Zap, 
  Flame, 
  Users, 
  Timer, 
  Plus, 
  Search, 
  Calendar, 
  Clock, 
  TrendingUp, 
  CheckCircle2, 
  X, 
  ShoppingBag, 
  ArrowUpRight 
} from 'lucide-react';

export interface FlashSaleActivity {
  id: string;
  name: string;
  type: 'flash_sale' | 'group_buy';
  productName: string;
  productImage: string;
  originalPrice: number;
  promoPrice: number;
  activityStock: number;
  soldStock: number;
  groupRequiredCount?: number; // e.g. 2人团 / 3人团
  status: 'ongoing' | 'upcoming' | 'ended';
  startTime: string;
  endTime: string;
}

const mockActivities: FlashSaleActivity[] = [
  {
    id: 'act-001',
    name: '【整点秒杀】极客降噪无线耳机 5折直降',
    type: 'flash_sale',
    productName: '极客降噪头戴式无线蓝牙耳机 Pro Max',
    productImage: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=200&auto=format&fit=crop&q=80',
    originalPrice: 1299,
    promoPrice: 649,
    activityStock: 200,
    soldStock: 178,
    status: 'ongoing',
    startTime: '2026-08-29 10:00',
    endTime: '2026-08-29 22:00'
  },
  {
    id: 'act-002',
    name: '【2人拼团】智能磁吸无线充电底座',
    type: 'group_buy',
    productName: '三合一折叠便携磁吸无线快充底座',
    productImage: 'https://images.unsplash.com/photo-1586816879360-004f5b0c51e3?w=200&auto=format&fit=crop&q=80',
    originalPrice: 299,
    promoPrice: 159,
    activityStock: 500,
    soldStock: 342,
    groupRequiredCount: 2,
    status: 'ongoing',
    startTime: '2026-08-28 00:00',
    endTime: '2026-08-30 23:59'
  },
  {
    id: 'act-003',
    name: '【午间专场】人体工学太空记忆护颈枕',
    type: 'flash_sale',
    productName: '慢回弹太空记忆棉深睡护颈枕',
    productImage: 'https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=200&auto=format&fit=crop&q=80',
    originalPrice: 199,
    promoPrice: 89,
    activityStock: 150,
    soldStock: 0,
    status: 'upcoming',
    startTime: '2026-08-30 12:00',
    endTime: '2026-08-30 14:00'
  },
  {
    id: 'act-004',
    name: '【3人成团】天然有机大马士革玫瑰纯露',
    type: 'group_buy',
    productName: '大马士革玫瑰舒缓补水喷雾 200ml',
    productImage: 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=200&auto=format&fit=crop&q=80',
    originalPrice: 168,
    promoPrice: 79,
    activityStock: 300,
    soldStock: 300,
    groupRequiredCount: 3,
    status: 'ended',
    startTime: '2026-08-25 00:00',
    endTime: '2026-08-27 23:59'
  }
];

export const FlashSaleManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [activities, setActivities] = useState<FlashSaleActivity[]>(mockActivities);
  const [tabType, setTabType] = useState<'all' | 'flash_sale' | 'group_buy'>('all');
  const [isModalOpen, setIsModalOpen] = useState(false);

  const filtered = activities.filter((a) => (tabType === 'all' ? true : a.type === tabType));

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              限时秒杀与拼团大促 (Flash Sale & Group Buy)
            </h2>
            <span className="text-xs bg-orange-50 text-orange-700 font-semibold px-2 py-0.5 rounded-full border border-orange-200">
              爆发引流
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            配置整点秒杀排期、爆品限量直降、老带新拼团活动及爆单实时监控。
          </p>
        </div>

        <button
          onClick={() => {
            showToast('已进入活动创建向导', 'info');
            setIsModalOpen(true);
          }}
          className="h-[36px] px-4 rounded-lg bg-orange-600 text-white hover:bg-orange-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs transition-colors cursor-pointer"
        >
          <Plus className="w-4 h-4" />
          <span>新建秒杀/拼团活动</span>
        </button>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-2 border-b border-gray-200 pb-2">
        <button
          onClick={() => setTabType('all')}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors ${
            tabType === 'all' ? 'bg-orange-600 text-white' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          全部活动 ({activities.length})
        </button>
        <button
          onClick={() => setTabType('flash_sale')}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors ${
            tabType === 'flash_sale' ? 'bg-orange-600 text-white' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          整点秒杀 (Flash Sales)
        </button>
        <button
          onClick={() => setTabType('group_buy')}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors ${
            tabType === 'group_buy' ? 'bg-orange-600 text-white' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          多人拼团 (Group Buying)
        </button>
      </div>

      {/* Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {filtered.map((act) => {
          const progress = Math.min(100, (act.soldStock / act.activityStock) * 100);

          return (
            <div
              key={act.id}
              className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 hover:border-orange-300 transition-all"
            >
              <div className="flex items-start justify-between gap-3 mb-3">
                <div className="flex items-center gap-2">
                  {act.type === 'flash_sale' ? (
                    <span className="p-1.5 bg-red-50 text-red-600 rounded-lg">
                      <Zap className="w-4 h-4" />
                    </span>
                  ) : (
                    <span className="p-1.5 bg-purple-50 text-purple-600 rounded-lg">
                      <Users className="w-4 h-4" />
                    </span>
                  )}
                  <span className="font-bold text-sm text-gray-900 line-clamp-1">{act.name}</span>
                </div>

                {act.status === 'ongoing' && (
                  <span className="flex items-center gap-1 text-[11px] font-semibold text-red-600 bg-red-50 px-2 py-0.5 rounded-full shrink-0">
                    <Flame className="w-3.5 h-3.5 animate-pulse" /> 进行中
                  </span>
                )}
                {act.status === 'upcoming' && (
                  <span className="text-[11px] font-semibold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-full shrink-0">
                    即将开始
                  </span>
                )}
                {act.status === 'ended' && (
                  <span className="text-[11px] font-semibold text-gray-500 bg-gray-100 px-2 py-0.5 rounded-full shrink-0">
                    已结束
                  </span>
                )}
              </div>

              <div className="flex gap-4 items-center">
                <img
                  src={act.productImage}
                  alt={act.productName}
                  className="w-20 h-20 rounded-lg object-cover border border-gray-100 shrink-0"
                />
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-gray-700 line-clamp-1">{act.productName}</p>
                  
                  <div className="flex items-baseline gap-2 mt-1.5">
                    <span className="text-lg font-bold text-red-600">¥{act.promoPrice}</span>
                    <span className="text-xs text-gray-400 line-through">原价 ¥{act.originalPrice}</span>
                    {act.groupRequiredCount && (
                      <span className="text-[11px] bg-purple-50 text-purple-700 px-1.5 py-0.2 rounded font-semibold">
                        {act.groupRequiredCount}人团
                      </span>
                    )}
                  </div>

                  {/* Stock progress */}
                  <div className="mt-2.5">
                    <div className="flex justify-between text-[11px] text-gray-500 mb-1">
                      <span>已售 {act.soldStock} 件</span>
                      <span>限量 {act.activityStock} 件 ({progress.toFixed(0)}%)</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-1.5 overflow-hidden">
                      <div
                        className="bg-orange-500 h-1.5 rounded-full"
                        style={{ width: `${progress}%` }}
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-4 pt-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-500">
                <span className="flex items-center gap-1 font-mono">
                  <Clock className="w-3.5 h-3.5" />
                  {act.startTime} ~ {act.endTime}
                </span>
                <button
                  onClick={() => showToast(`已加载【${act.name}】活动详情与成团明细`, 'info')}
                  className="text-orange-600 hover:text-orange-700 font-semibold flex items-center gap-0.5"
                >
                  活动看板 <ArrowUpRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">配置新营销大促活动</h3>
              <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="py-4 space-y-3 text-sm">
              <p className="text-xs text-gray-600">
                支持设置限时立减直降或老带新裂变拼团，系统将自动在移动端首页 Banner 与秒杀专区上架展示。
              </p>
              <div className="p-3 bg-orange-50 rounded-lg text-xs text-orange-800">
                💡 建议在每日前 2 小时进行限时特惠，成单转化率平均提升 320%。
              </div>
            </div>
            <div className="pt-3 border-t border-gray-200 flex justify-end">
              <button
                onClick={() => {
                  setIsModalOpen(false);
                  showToast('活动草稿已保存并推送到排期日历', 'success');
                }}
                className="px-4 py-2 bg-orange-600 text-white text-xs font-semibold rounded-lg hover:bg-orange-700"
              >
                完成并排期发布
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
