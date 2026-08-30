import React, { useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { listContentReviews, replyContentReview, updateContentReviewStatus } from '../../api/adminApi';
import { 
  Star, 
  MessageSquare, 
  Search, 
  ThumbsUp, 
  CheckCircle, 
  CornerDownRight, 
  ShieldCheck, 
  AlertCircle, 
  Send 
} from 'lucide-react';

export interface ReviewItem {
  id: string;
  userName: string;
  userAvatar: string;
  productName: string;
  rating: number;
  content: string;
  images?: string[];
  reply?: string;
  status: 'approved' | 'pending' | 'hidden';
  createdAt: string;
  likes: number;
  isFeatured?: boolean;
}

const mockReviews: ReviewItem[] = [
  {
    id: 'rev-001',
    userName: '数码发烧友_Alan',
    userAvatar: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&auto=format&fit=crop&q=80',
    productName: '极客降噪无线蓝牙耳机 Pro Max',
    rating: 5,
    content: '降噪效果非常惊艳！在高铁和飞机上戴上瞬间安静，音质高中低频分离度极高，佩戴舒适不压耳，强烈推荐！',
    images: [
      'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=300&auto=format&fit=crop&q=80'
    ],
    reply: '感谢您的认可与支持！耳机支持 OTA 固件升级，后续还将持续优化音质算法与续航表现，祝您使用愉快！',
    status: 'approved',
    createdAt: '2026-08-28 14:20:00',
    likes: 42,
    isFeatured: true
  },
  {
    id: 'rev-002',
    userName: '小西爱生活',
    userAvatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=80',
    productName: '天然有机大马士革玫瑰纯露 200ml',
    rating: 5,
    content: '味道非常天然纯正的淡淡玫瑰香，喷雾很细腻，换季敏感湿敷特别舒服，已经第二次回购了。',
    status: 'approved',
    createdAt: '2026-08-29 09:15:00',
    likes: 18,
    isFeatured: false
  },
  {
    id: 'rev-003',
    userName: '匿名买家',
    userAvatar: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100&auto=format&fit=crop&q=80',
    productName: '智能磁吸无线快充底座',
    rating: 2,
    content: '物流速度还行，但是充电时发热稍微有点明显，咨询客服回复稍慢。',
    reply: '',
    status: 'pending',
    createdAt: '2026-08-29 11:40:00',
    likes: 3,
    isFeatured: false
  }
];

export const ReviewManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [reviews, setReviews] = useState<ReviewItem[]>(mockReviews);
  const [replyTextMap, setReplyTextMap] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    listContentReviews({ current: 1, size: 100 })
      .then((page) => {
        if (cancelled) return;
        setReviews((page.records || []).map((review) => ({
          id: String(review.id),
          userName: review.memberName,
          userAvatar: review.memberAvatarUrl || '',
          productName: `商品 #${review.productId}`,
          rating: review.rating,
          content: review.reviewContent,
          reply: review.replyContent || '',
          status: review.status === 1 ? 'approved' : 'pending',
          createdAt: review.createdAt || review.reviewedAt || '',
          likes: review.helpfulCount || 0,
        })));
      })
      .catch(() => showToast('评价接口暂不可用，当前显示演示数据', 'warning'))
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const handleReplySubmit = async (id: string) => {
    const text = replyTextMap[id];
    if (!text || !text.trim()) {
      showToast('请输入回复内容', 'error');
      return;
    }
    try {
      await replyContentReview(Number(id), text.trim());
      setReviews((prev) => prev.map((r) => (r.id === id ? { ...r, reply: text.trim() } : r)));
      setReplyTextMap((prev) => ({ ...prev, [id]: '' }));
      showToast('官方回复已发布并通知客户', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '回复发布失败，请稍后重试', 'error');
    }
  };

  const handleToggleStatus = async (review: ReviewItem) => {
    const nextStatus = review.status === 'approved' ? 0 : 1;
    try {
      await updateContentReviewStatus(Number(review.id), nextStatus);
      setReviews((prev) => prev.map((item) => item.id === review.id
        ? { ...item, status: nextStatus === 1 ? 'approved' : 'hidden' }
        : item));
      showToast(nextStatus === 1 ? '评价已通过审核' : '评价已隐藏', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '评价状态更新失败，请稍后重试', 'error');
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              客户评价与晒单管理 (Reviews & Ratings)
            </h2>
            <span className="text-xs bg-amber-50 text-amber-700 font-semibold px-2 py-0.5 rounded-full border border-amber-200">
              口碑阵地
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            买家真实口碑、晒图审核、差评安抚拦截、官方客服快捷回复与精选置顶。
          </p>
        </div>
      </div>

      {/* Review List */}
      {loading && <div className="text-sm text-gray-500">正在加载真实评价数据…</div>}
      <div className="space-y-4">
        {!loading && reviews.length === 0 && (
          <div className="rounded-xl border border-dashed border-gray-300 bg-white py-12 text-center text-sm text-gray-400">
            暂无评价数据
          </div>
        )}
        {reviews.map((rev) => (
          <div
            key={rev.id}
            className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 hover:border-gray-300 transition-all"
          >
            <div className="flex items-start justify-between gap-4 mb-3">
              <div className="flex items-center gap-3">
                <img
                  src={rev.userAvatar}
                  alt={rev.userName}
                  className="w-10 h-10 rounded-full object-cover border border-gray-200"
                />
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-gray-900 text-sm">{rev.userName}</span>
                    {rev.isFeatured && (
                      <span className="text-[10px] font-bold bg-amber-100 text-amber-800 px-1.5 py-0.2 rounded">
                        ★ 精选晒单
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-gray-400 mt-0.5">购买商品：{rev.productName}</div>
                </div>
              </div>

              <div className="flex items-center gap-1">
                {[...Array(5)].map((_, i) => (
                  <Star
                    key={i}
                    className={`w-4 h-4 ${
                      i < rev.rating ? 'fill-amber-400 text-amber-400' : 'text-gray-200'
                    }`}
                  />
                ))}
              </div>
            </div>

            <p className="text-sm text-gray-800 leading-relaxed mb-3">{rev.content}</p>

            {rev.images && rev.images.length > 0 && (
              <div className="flex gap-2 mb-3">
                {rev.images.map((img, idx) => (
                  <img
                    key={idx}
                    src={img}
                    alt="review proof"
                    className="w-20 h-20 rounded-lg object-cover border border-gray-200"
                  />
                ))}
              </div>
            )}

            {/* Official Reply */}
            {rev.reply && (
              <div className="p-3 bg-blue-50/70 rounded-lg border border-blue-100 text-xs text-blue-900 mb-3 flex items-start gap-2">
                <CornerDownRight className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
                <div>
                  <strong className="text-blue-700 block mb-0.5">卖家官方回复：</strong>
                  <span>{rev.reply}</span>
                </div>
              </div>
            )}

            {/* Reply Input if no reply */}
            {!rev.reply && (
              <div className="flex gap-2 mb-3">
                <input
                  type="text"
                  value={replyTextMap[rev.id] || ''}
                  onChange={(e) =>
                    setReplyTextMap({ ...replyTextMap, [rev.id]: e.target.value })
                  }
                  placeholder="写下温暖专业的官方回复..."
                  className="flex-1 h-[34px] px-3 text-xs rounded-lg border border-gray-300 outline-none"
                />
                <button
                  onClick={() => handleReplySubmit(rev.id)}
                  className="h-[34px] px-3.5 bg-blue-600 text-white rounded-lg text-xs font-semibold hover:bg-blue-700 flex items-center gap-1 shrink-0"
                >
                  <Send className="w-3.5 h-3.5" /> 回复
                </button>
              </div>
            )}

            <div className="flex items-center justify-between text-xs text-gray-400 pt-2 border-t border-gray-100">
              <span>{rev.createdAt} · 赞同 {rev.likes}</span>
              <button
                onClick={() => void handleToggleStatus(rev)}
                className="text-gray-500 hover:text-amber-600 font-semibold"
              >
                {rev.status === 'approved' ? '隐藏评价' : '通过审核'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
