import React from 'react';
import { Product } from '../types/ecommerce';
import { Star, ShoppingBag, Eye, Heart, Check, Scale } from 'lucide-react';

interface ProductCardProps {
  product: Product;
  isWishlisted: boolean;
  isCompareMode?: boolean;
  isCompared?: boolean;
  viewMode?: 'grid' | 'list';
  onQuickView: (product: Product) => void;
  onAddToCart: (product: Product) => void;
  onToggleWishlist: (productId: string) => void;
  onToggleCompare?: (product: Product) => void;
}

export const ProductCard: React.FC<ProductCardProps> = ({
  product,
  isWishlisted,
  isCompareMode = false,
  isCompared = false,
  viewMode = 'grid',
  onQuickView,
  onAddToCart,
  onToggleWishlist,
  onToggleCompare,
}) => {
  const discountPercent = Math.round(
    ((product.originalPrice - product.price) / product.originalPrice) * 100
  );
  const stockState = product.stock <= 0 ? 'out' : product.stock <= 10 ? 'low' : 'in';
  const stockLabel = stockState === 'out' ? '暂时缺货' : stockState === 'low' ? `仅剩 ${product.stock} 件` : '有库存';

  if (viewMode === 'list') {
    return (
      <div
        className={`group bg-white rounded-2xl border transition-all p-4 hover:shadow-md flex flex-col sm:flex-row gap-5 items-start sm:items-center ${
          isCompared
            ? 'border-zinc-900 ring-2 ring-zinc-900/10 shadow-sm bg-zinc-50/30'
            : 'border-zinc-200/90 hover:border-zinc-300'
        }`}
      >
        {/* Image */}
        <div
          onClick={() => onQuickView(product)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              onQuickView(product);
            }
          }}
          role="button"
          tabIndex={0}
          aria-label={`查看商品 ${product.title} 详情`}
          className="relative w-full sm:w-48 h-48 rounded-xl overflow-hidden bg-zinc-100 shrink-0 cursor-pointer"
        >
          <img
            src={product.images[0]}
            alt={product.title}
            className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500"
          />
          {product.badge && (
            <span className="absolute top-2.5 left-2.5 px-2 py-0.5 rounded-md bg-zinc-900/90 text-white text-[10px] font-bold tracking-wider shadow-xs">
              {product.badge}
            </span>
          )}
          {onToggleCompare && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onToggleCompare(product);
              }}
              className={`absolute top-2.5 right-2.5 px-2.5 py-1 rounded-lg text-xs font-semibold flex items-center gap-1 backdrop-blur-md shadow-xs transition ${
                isCompared
                  ? 'bg-zinc-900 text-white'
                  : 'bg-white/90 text-zinc-700 hover:bg-white'
              }`}
              aria-pressed={isCompared}
              aria-label={isCompared ? `取消对比 ${product.title}` : `加入对比 ${product.title}`}
            >
              <Scale className="w-3 h-3" />
              <span>{isCompared ? '已加入对比' : '对比'}</span>
            </button>
          )}
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
              {product.brand}
            </span>
            <span className="text-zinc-300">•</span>
            <span className="text-xs text-zinc-500">{product.categoryLabel}</span>
          </div>

          <h3
            onClick={() => onQuickView(product)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onQuickView(product);
              }
            }}
            role="button"
            tabIndex={0}
            aria-label={`查看商品 ${product.title} 详情`}
            className="text-base font-semibold text-zinc-900 hover:text-zinc-600 transition cursor-pointer mb-1.5 line-clamp-1"
          >
            {product.title}
          </h3>

          <p className="text-xs text-zinc-500 line-clamp-2 mb-3 leading-relaxed">
            {product.subtitle}
          </p>

          <div className="flex items-center gap-4 text-xs text-zinc-500 mb-3">
            <div className="flex items-center gap-1 text-amber-500 font-medium">
              <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
              <span>{product.rating}</span>
              <span className="text-zinc-400">({product.reviewCount}条评价)</span>
            </div>
            <span>已售 {product.salesCount}+</span>
            <span className={stockState === 'out' ? 'text-rose-600 font-medium' : stockState === 'low' ? 'text-amber-600 font-medium' : 'text-emerald-600 font-medium'}>{stockLabel}</span>
            <span className="text-emerald-600 font-medium">顺丰包邮</span>
          </div>

          <div className="flex items-center justify-between gap-4 pt-2 border-t border-zinc-100">
            <div className="flex items-baseline gap-2">
              <span className="text-xs font-bold text-zinc-900">¥</span>
              <span className="text-2xl font-black text-zinc-900 tracking-tight">
                {product.price}
              </span>
              <span className="text-xs text-zinc-400 line-through">
                ¥{product.originalPrice}
              </span>
              {discountPercent > 0 && (
                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-rose-50 text-rose-600">
                  省{product.originalPrice - product.price}元
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              {onToggleCompare && (
                <button
                  onClick={() => onToggleCompare(product)}
                  className={`p-2.5 rounded-xl border transition flex items-center gap-1.5 text-xs font-medium ${
                    isCompared
                      ? 'bg-zinc-900 border-zinc-900 text-white'
                      : 'border-zinc-200 text-zinc-600 hover:text-zinc-900 hover:bg-zinc-50'
                  }`}
                  title={isCompared ? '取消对比' : '加入对比'}
                  aria-pressed={isCompared}
                  aria-label={isCompared ? `取消对比 ${product.title}` : `加入对比 ${product.title}`}
                >
                  <Scale className="w-4 h-4" />
                  <span className="hidden sm:inline">{isCompared ? '已对比' : '对比'}</span>
                </button>
              )}

              <button
                onClick={() => onToggleWishlist(product.id)}
                className={`p-2.5 rounded-xl border transition ${
                  isWishlisted
                    ? 'bg-rose-50 border-rose-200 text-rose-500'
                    : 'border-zinc-200 text-zinc-500 hover:text-zinc-900 hover:bg-zinc-50'
                }`}
                title={isWishlisted ? '取消收藏' : '加入收藏'}
                aria-pressed={isWishlisted}
                aria-label={isWishlisted ? `取消收藏 ${product.title}` : `收藏 ${product.title}`}
              >
                <Heart className={`w-4 h-4 ${isWishlisted ? 'fill-rose-500' : ''}`} />
              </button>

              <button
                onClick={() => onQuickView(product)}
                aria-label={`查看商品 ${product.title} 详情`}
                className="px-3.5 py-2.5 rounded-xl border border-zinc-200 text-zinc-700 hover:bg-zinc-50 text-xs font-medium transition"
              >
                查看详情
              </button>

              <button
                onClick={() => onAddToCart(product)}
                disabled={stockState === 'out'}
                aria-label={stockState === 'out' ? `${product.title} 暂时缺货` : `将 ${product.title} 加入购物车`}
                className={`inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-xs font-semibold transition shadow-xs ${stockState === 'out' ? 'bg-zinc-200 text-zinc-400 cursor-not-allowed' : 'bg-zinc-900 text-white hover:bg-zinc-800'}`}
              >
                <ShoppingBag className="w-3.5 h-3.5" />
                加入购物车
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      className={`group bg-white rounded-2xl border transition-all hover:shadow-lg flex flex-col overflow-hidden relative ${
        isCompared
          ? 'border-zinc-900 ring-2 ring-zinc-900/10 shadow-sm bg-zinc-50/20'
          : 'border-zinc-200/90 hover:border-zinc-300'
      }`}
    >
      {/* Product Image Stage */}
      <div
        onClick={() => onQuickView(product)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onQuickView(product);
          }
        }}
        role="button"
        tabIndex={0}
        aria-label={`查看商品 ${product.title} 详情`}
        className="relative aspect-square w-full bg-zinc-100 overflow-hidden cursor-pointer"
      >
        <img
          src={product.images[0]}
          alt={product.title}
          className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500"
        />

        {/* Top Badges */}
        <div className="absolute top-3 left-3 flex flex-col gap-1 z-10">
          {product.badge && (
            <span className="px-2.5 py-0.5 rounded-md bg-zinc-900 text-white text-[10px] font-bold tracking-wider shadow-xs">
              {product.badge}
            </span>
          )}
          {discountPercent > 0 && (
            <span className="px-2.5 py-0.5 rounded-md bg-rose-600 text-white text-[10px] font-bold shadow-xs">
              -{discountPercent}%
            </span>
          )}
        </div>

        {/* Compare Checkbox Button */}
        {onToggleCompare && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onToggleCompare(product);
            }}
            className={`absolute top-3 ${
              product.badge || discountPercent > 0 ? 'left-auto right-12' : 'left-3'
            } z-10 px-2 py-1 rounded-lg flex items-center gap-1.5 backdrop-blur-md transition-all shadow-xs text-[11px] font-bold ${
              isCompared
                ? 'bg-zinc-900 text-white ring-1 ring-zinc-900'
                : isCompareMode
                ? 'bg-white/95 text-zinc-800 border border-zinc-300 hover:bg-white shadow-xs'
                : 'bg-white/85 text-zinc-700 hover:bg-white opacity-0 group-hover:opacity-100'
            }`}
            title={isCompared ? '取消对比' : '加入商品对比'}
            aria-pressed={isCompared}
            aria-label={isCompared ? `取消对比 ${product.title}` : `加入对比 ${product.title}`}
          >
            <div
              className={`w-3.5 h-3.5 rounded flex items-center justify-center border ${
                isCompared
                  ? 'bg-amber-400 border-amber-400 text-zinc-950'
                  : 'border-zinc-400 bg-white'
              }`}
            >
              {isCompared && <Check className="w-2.5 h-2.5 stroke-[3]" />}
            </div>
            <span>{isCompared ? '已对比' : '对比'}</span>
          </button>
        )}

        {/* Top Right Wishlist Button */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            onToggleWishlist(product.id);
          }}
          className={`absolute top-3 right-3 z-10 w-8 h-8 rounded-full flex items-center justify-center backdrop-blur-md transition-all shadow-xs ${
            isWishlisted
              ? 'bg-rose-50 text-rose-500 ring-1 ring-rose-200'
              : 'bg-white/80 text-zinc-600 hover:bg-white hover:text-zinc-900 opacity-90 group-hover:opacity-100'
          }`}
          title={isWishlisted ? '取消收藏' : '加入收藏'}
          aria-pressed={isWishlisted}
          aria-label={isWishlisted ? `取消收藏 ${product.title}` : `收藏 ${product.title}`}
        >
          <Heart className={`w-4 h-4 ${isWishlisted ? 'fill-rose-500' : ''}`} />
        </button>

        {/* Hover Quick Action Buttons */}
        <div className="absolute inset-x-3 bottom-3 z-10 flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-all duration-200 transform translate-y-2 group-hover:translate-y-0">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onQuickView(product);
            }}
            className="flex-1 py-2 rounded-xl bg-white/95 backdrop-blur-md text-zinc-900 hover:bg-white text-xs font-semibold shadow-md flex items-center justify-center gap-1.5 transition"
            aria-label={`快速预览 ${product.title}`}
          >
            <Eye className="w-3.5 h-3.5" />
            快速预览
          </button>
        </div>
      </div>

      {/* Product Content Details */}
      <div className="p-4 sm:p-5 flex-1 flex flex-col justify-between">
        <div>
          <div className="flex items-center justify-between gap-2 mb-1.5">
            <span className="text-[11px] font-bold text-zinc-400 uppercase tracking-wider">
              {product.brand}
            </span>
            <span className="text-[11px] font-medium text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded">
              顺丰包邮
            </span>
          </div>

          <h3
            onClick={() => onQuickView(product)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onQuickView(product);
              }
            }}
            role="button"
            tabIndex={0}
            aria-label={`查看商品 ${product.title} 详情`}
            className="text-sm font-semibold text-zinc-900 group-hover:text-zinc-700 transition cursor-pointer mb-1 line-clamp-2 leading-snug"
          >
            {product.title}
          </h3>

          <p className="text-xs text-zinc-500 line-clamp-1 mb-3">
            {product.subtitle}
          </p>
        </div>

        <div>
          {/* Rating & Sales */}
          <div className="flex items-center justify-between text-xs text-zinc-500 mb-3 pt-2 border-t border-zinc-100">
            <div className="flex items-center gap-1 text-amber-500 font-medium">
              <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
              <span>{product.rating}</span>
              <span className="text-zinc-400">({product.reviewCount})</span>
            </div>
            <span className="text-[11px] text-zinc-400">已售 {product.salesCount}+</span>
          </div>
          <div className={`text-[11px] mb-3 ${stockState === 'out' ? 'text-rose-600' : stockState === 'low' ? 'text-amber-600' : 'text-emerald-600'}`}>
            {stockLabel}
          </div>

          {/* Pricing & Add to Cart */}
          <div className="flex items-center justify-between gap-2">
            <div className="flex flex-col">
              <div className="flex items-baseline gap-1">
                <span className="text-xs font-bold text-zinc-900">¥</span>
                <span className="text-xl font-black text-zinc-900 tracking-tight">
                  {product.price}
                </span>
              </div>
              <span className="text-[11px] text-zinc-400 line-through -mt-1">
                ¥{product.originalPrice}
              </span>
            </div>

            <button
              onClick={() => onAddToCart(product)}
              disabled={stockState === 'out'}
              aria-label={stockState === 'out' ? `${product.title} 暂时缺货` : `将 ${product.title} 加入购物车`}
              className={`inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition active:scale-95 shadow-xs ${stockState === 'out' ? 'bg-zinc-200 text-zinc-400 cursor-not-allowed' : 'bg-zinc-900 text-white hover:bg-zinc-800'}`}
              title="加入购物车"
            >
              <ShoppingBag className="w-3.5 h-3.5" />
              <span>加购物车</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
