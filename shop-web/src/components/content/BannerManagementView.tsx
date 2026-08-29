import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { 
  Image as ImageIcon, 
  Plus, 
  Eye, 
  Edit3, 
  Trash2, 
  CheckCircle2, 
  Clock, 
  ArrowUpRight, 
  Sparkles, 
  ExternalLink 
} from 'lucide-react';

export interface BannerItem {
  id: string;
  title: string;
  position: 'home_top' | 'category_top' | 'popup_modal';
  imageUrl: string;
  targetLink: string;
  clickCount: number;
  viewCount: number;
  status: 'active' | 'scheduled' | 'disabled';
  startDate: string;
  endDate: string;
  sort: number;
}

const mockBanners: BannerItem[] = [
  {
    id: 'ban-001',
    title: '秋季数码开学焕新季大促',
    position: 'home_top',
    imageUrl: 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600&auto=format&fit=crop&q=80',
    targetLink: '/pages/activity/autumn-digital',
    clickCount: 14200,
    viewCount: 98000,
    status: 'active',
    startDate: '2026-08-20',
    endDate: '2026-09-15',
    sort: 1
  },
  {
    id: 'ban-002',
    title: '极客降噪耳机 Pro 首发直降¥650',
    position: 'home_top',
    imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&auto=format&fit=crop&q=80',
    targetLink: '/pages/product/p-1',
    clickCount: 8900,
    viewCount: 65400,
    status: 'active',
    startDate: '2026-08-25',
    endDate: '2026-09-05',
    sort: 2
  },
  {
    id: 'ban-003',
    title: '新用户注册领 ¥188 专属礼包',
    position: 'popup_modal',
    imageUrl: 'https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=600&auto=format&fit=crop&q=80',
    targetLink: '/pages/coupon/new-user',
    clickCount: 21300,
    viewCount: 42000,
    status: 'active',
    startDate: '2026-08-01',
    endDate: '2026-10-31',
    sort: 1
  }
];

export const BannerManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [banners, setBanners] = useState<BannerItem[]>(mockBanners);

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              轮播海报与页面装修 (Banners & CMS)
            </h2>
            <span className="text-xs bg-rose-50 text-rose-700 font-semibold px-2 py-0.5 rounded-full border border-rose-200">
              视觉营销
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            配置小程序与App首页焦点轮播图、弹窗活动公告及曝光点击转化分析。
          </p>
        </div>

        <button
          onClick={() => showToast('已打开海报位创建与素材上传窗口', 'info')}
          className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs"
        >
          <Plus className="w-4 h-4" />
          <span>新增轮播海报</span>
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {banners.map((ban) => {
          const ctr = ban.viewCount > 0 ? ((ban.clickCount / ban.viewCount) * 100).toFixed(1) : '0';

          return (
            <div
              key={ban.id}
              className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden hover:border-blue-300 transition-all flex flex-col justify-between"
            >
              <div>
                <div className="relative h-36 w-full bg-gray-100 overflow-hidden">
                  <img
                    src={ban.imageUrl}
                    alt={ban.title}
                    className="w-full h-full object-cover hover:scale-105 transition-transform duration-300"
                  />
                  <div className="absolute top-2 left-2">
                    <span className="text-[11px] font-bold bg-black/60 text-white px-2 py-0.5 rounded backdrop-blur-xs">
                      {ban.position === 'home_top' ? '首页主轮播' : '全局弹窗'}
                    </span>
                  </div>
                  <div className="absolute top-2 right-2">
                    <span className="text-[11px] font-bold bg-emerald-500 text-white px-2 py-0.5 rounded shadow-xs">
                      进行中
                    </span>
                  </div>
                </div>

                <div className="p-4">
                  <h3 className="font-bold text-gray-900 text-sm mb-1 line-clamp-1">{ban.title}</h3>
                  <div className="text-xs text-gray-400 font-mono mb-3 flex items-center gap-1">
                    <ExternalLink className="w-3 h-3 text-blue-500" /> {ban.targetLink}
                  </div>

                  <div className="grid grid-cols-3 gap-2 bg-gray-50 p-2.5 rounded-lg text-center text-xs">
                    <div>
                      <span className="text-gray-400 block text-[10px]">曝光量 PV</span>
                      <strong className="text-gray-800 font-mono">{(ban.viewCount / 1000).toFixed(1)}k</strong>
                    </div>
                    <div>
                      <span className="text-gray-400 block text-[10px]">点击量 UV</span>
                      <strong className="text-blue-600 font-mono">{(ban.clickCount / 1000).toFixed(1)}k</strong>
                    </div>
                    <div>
                      <span className="text-gray-400 block text-[10px]">点击转化率</span>
                      <strong className="text-emerald-700 font-mono">{ctr}%</strong>
                    </div>
                  </div>
                </div>
              </div>

              <div className="p-4 pt-0 flex items-center justify-between text-xs text-gray-400">
                <span>{ban.startDate} ~ {ban.endDate}</span>
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => showToast('海报位已刷新并重新排序', 'success')}
                    className="p-1 text-gray-400 hover:text-blue-600 rounded"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => {
                      setBanners(banners.filter((b) => b.id !== ban.id));
                      showToast('海报已下线移除', 'success');
                    }}
                    className="p-1 text-gray-400 hover:text-red-600 rounded"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
