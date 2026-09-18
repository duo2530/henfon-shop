import React from 'react';
import { useBodyScrollLock } from '../hooks/useBodyScrollLock';
import { Product } from '../types/ecommerce';
import { Heart, ShoppingBag, Trash2, X, ArrowRight } from 'lucide-react';

interface WishlistModalProps {
  isOpen: boolean;
  wishlistedProducts: Product[];
  onClose: () => void;
  onAddToCart: (product: Product) => void;
  onRemoveWishlist: (productId: string) => void;
  onQuickView: (product: Product) => void;
}

export const WishlistModal: React.FC<WishlistModalProps> = ({
  isOpen,
  wishlistedProducts,
  onClose,
  onAddToCart,
  onRemoveWishlist,
  onQuickView,
}) => {
  useBodyScrollLock(isOpen);
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/60 backdrop-blur-xs flex items-center justify-center p-3 sm:p-6 animate-in fade-in duration-200">
      <div 
        className="bg-white rounded-3xl border border-zinc-200 shadow-2xl max-w-2xl w-full overflow-hidden relative flex flex-col max-h-[85vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-5 sm:p-6 border-b border-zinc-200 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-rose-50 text-rose-500 border border-rose-100 flex items-center justify-center">
              <Heart className="w-4 h-4 fill-rose-500" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-zinc-900">我的心愿收藏</h2>
              <p className="text-xs text-zinc-500">共收藏 {wishlistedProducts.length} 件心仪好物</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-zinc-100 text-zinc-500 hover:text-zinc-900 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* List of Wishlisted Items */}
        <div className="overflow-y-auto p-5 sm:p-6 space-y-3 flex-1">
          {wishlistedProducts.length === 0 ? (
            <div className="py-16 text-center text-zinc-400 space-y-3">
              <Heart className="w-12 h-12 mx-auto stroke-1 text-zinc-300" />
              <p className="text-sm font-semibold text-zinc-700">暂无收藏的商品</p>
              <p className="text-xs text-zinc-400">点击商品卡片右上角的爱心图标即可快速加入心愿单</p>
            </div>
          ) : (
            wishlistedProducts.map((p) => (
              <div
                key={p.id}
                className="p-3.5 rounded-2xl border border-zinc-200/90 bg-white hover:border-zinc-300 transition flex items-center justify-between gap-4"
              >
                <div 
                  onClick={() => onQuickView(p)}
                  className="flex items-center gap-3 min-w-0 cursor-pointer group"
                >
                  <img
                    src={p.images[0]}
                    alt={p.title}
                    className="w-14 h-14 rounded-xl object-cover bg-zinc-100 border border-zinc-200 shrink-0 group-hover:scale-105 transition"
                  />
                  <div className="min-w-0">
                    <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">
                      {p.brand}
                    </span>
                    <h4 className="text-xs font-semibold text-zinc-900 group-hover:text-zinc-600 transition truncate">
                      {p.title}
                    </h4>
                    <div className="flex items-baseline gap-1.5 mt-0.5">
                      <span className="text-xs font-bold text-zinc-900">¥{p.price}</span>
                      <span className="text-[10px] text-zinc-400 line-through">¥{p.originalPrice}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => onAddToCart(p)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-zinc-900 text-white hover:bg-zinc-800 text-xs font-semibold transition"
                  >
                    <ShoppingBag className="w-3.5 h-3.5" />
                    <span>移入购物车</span>
                  </button>
                  <button
                    onClick={() => onRemoveWishlist(p.id)}
                    className="p-2 rounded-xl text-zinc-400 hover:text-rose-500 hover:bg-rose-50 transition"
                    title="移除收藏"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
