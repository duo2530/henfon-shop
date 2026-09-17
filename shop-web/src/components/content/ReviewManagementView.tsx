import React, { useCallback, useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { listContentReviews, replyContentReview, updateContentReviewStatus } from '../../api/adminApi';
import { formatDateTime } from '../../utils/datetime';
import { 
  Star, 
  MessageSquare, 
  Search, 
  ThumbsUp, 
  CheckCircle, 
  CornerDownRight, 
  ShieldCheck, 
  AlertCircle, 
  Send,
  Loader2,
  RefreshCw
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

function reviewStatus(status: number): ReviewItem['status'] {
  // 后端约定：0隐藏、1展示；后台审核接口不再使用演示态“待审核”。
  if (status === 1) return 'approved';
  return 'hidden';
}

function parseReviewImages(imageUrls?: string): string[] {
  if (!imageUrls) return [];
  try {
    const parsed: unknown = JSON.parse(imageUrls);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string' && item.trim().length > 0) : [];
  } catch {
    // 兼容早期数据以逗号分隔图片地址的存储格式。
    return imageUrls.split(',').map((item) => item.trim()).filter(Boolean);
  }
}

export const ReviewManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [reviews, setReviews] = useState<ReviewItem[]>([]);
  const [replyTextMap, setReplyTextMap] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [replyingId, setReplyingId] = useState<string | null>(null);
  const [statusUpdatingId, setStatusUpdatingId] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | '0' | '1'>('all');

  const loadReviews = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const page = await listContentReviews({
        current: 1,
        size: 100,
        keyword: searchTerm.trim() || undefined,
        status: statusFilter === 'all' ? undefined : Number(statusFilter),
      });
      setReviews((page.records || []).map((review) => ({
        id: String(review.id),
        userName: review.memberName,
        userAvatar: review.memberAvatarUrl || '',
        productName: `商品 #${review.productId}`,
        rating: review.rating,
        content: review.reviewContent,
        images: parseReviewImages(review.imageUrls),
        reply: review.replyContent || '',
        status: reviewStatus(review.status),
        createdAt: formatDateTime(review.createdAt || review.reviewedAt, ''),
        likes: review.helpfulCount || 0,
      })));
    } catch (error) {
      const message = error instanceof Error ? error.message : '评价数据加载失败';
      setReviews([]);
      setLoadError(message);
      showToast(`${message}，请点击重试`, 'error');
    } finally {
      setLoading(false);
    }
  }, [searchTerm, showToast, statusFilter]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadReviews(), 250);
    return () => window.clearTimeout(timer);
  }, [loadReviews]);

  const handleReplySubmit = async (id: string) => {
    const text = replyTextMap[id];
    if (!text || !text.trim()) {
      showToast('请输入回复内容', 'error');
      return;
    }
    const previous = reviews;
    setReplyingId(id);
    setReviews((prev) => prev.map((review) => review.id === id ? { ...review, reply: text.trim() } : review));
    try {
      await replyContentReview(Number(id), text.trim());
      setReplyTextMap((prev) => ({ ...prev, [id]: '' }));
      showToast('官方回复已发布并通知客户', 'success');
    } catch (error) {
      setReviews(previous);
      showToast(error instanceof Error ? error.message : '回复发布失败，请稍后重试', 'error');
    } finally {
      setReplyingId(null);
    }
  };

  const handleToggleStatus = async (review: ReviewItem) => {
    const nextStatus = review.status === 'approved' ? 0 : 1;
    const previous = reviews;
    const nextFrontendStatus: ReviewItem['status'] = nextStatus === 1 ? 'approved' : 'hidden';
    setStatusUpdatingId(review.id);
    // 先更新界面提供即时反馈，接口失败时恢复快照，避免显示虚假的审核结果。
    setReviews((prev) => prev.map((item) => item.id === review.id ? { ...item, status: nextFrontendStatus } : item));
    try {
      await updateContentReviewStatus(Number(review.id), nextStatus);
      showToast(nextStatus === 1 ? '评价已通过审核' : '评价已隐藏', 'success');
    } catch (error) {
      setReviews(previous);
      showToast(error instanceof Error ? error.message : '评价状态更新失败，请稍后重试', 'error');
    } finally {
      setStatusUpdatingId(null);
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

      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-4 flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px] max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
          <input
            type="search"
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            placeholder="搜索评价内容或会员名称…"
            className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none focus:border-blue-500"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as 'all' | '0' | '1')}
          className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
          aria-label="评价展示状态"
        >
          <option value="all">全部状态</option>
          <option value="1">已展示</option>
          <option value="0">已隐藏</option>
        </select>
        <button
          type="button"
          onClick={() => void loadReviews()}
          className="h-[36px] px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5"
        >
          <RefreshCw className="w-4 h-4" />刷新
        </button>
      </div>

      {/* Review List */}
      {loading && <div className="flex items-center text-sm text-gray-500"><Loader2 className="w-4 h-4 mr-2 animate-spin" />正在加载真实评价数据…</div>}
      {!loading && loadError && (
        <div className="rounded-xl border border-red-200 bg-red-50 py-10 text-center text-sm text-red-700" role="alert">
          <AlertCircle className="w-6 h-6 mx-auto mb-2" />
          <p>{loadError}</p>
          <button type="button" onClick={() => void loadReviews()} className="mt-3 inline-flex items-center gap-1.5 text-blue-700 hover:underline"><RefreshCw className="w-4 h-4" />重新加载</button>
        </div>
      )}
      <div className="space-y-4">
        {!loading && !loadError && reviews.length === 0 && (
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
                  type="button"
                  disabled={replyingId === rev.id}
                  onClick={() => void handleReplySubmit(rev.id)}
                  className="h-[34px] px-3.5 bg-blue-600 text-white rounded-lg text-xs font-semibold hover:bg-blue-700 flex items-center gap-1 shrink-0 disabled:opacity-60"
                >
                  {replyingId === rev.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />} {replyingId === rev.id ? '发布中…' : '回复'}
                </button>
              </div>
            )}

            <div className="flex items-center justify-between text-xs text-gray-400 pt-2 border-t border-gray-100">
              <span className="flex items-center gap-2">{rev.createdAt} · 赞同 {rev.likes}
                <span className={`px-1.5 py-0.5 rounded ${rev.status === 'approved' ? 'bg-emerald-50 text-emerald-700' : rev.status === 'hidden' ? 'bg-gray-100 text-gray-600' : 'bg-amber-50 text-amber-700'}`}>
                  {rev.status === 'approved' ? '已通过' : rev.status === 'hidden' ? '已隐藏' : '待审核'}
                </span>
              </span>
              <button
                type="button"
                disabled={statusUpdatingId === rev.id}
                onClick={() => void handleToggleStatus(rev)}
                className="text-gray-500 hover:text-amber-600 font-semibold disabled:opacity-50"
              >
                {statusUpdatingId === rev.id ? '处理中…' : rev.status === 'approved' ? '隐藏评价' : rev.status === 'hidden' ? '重新展示' : '通过审核'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
