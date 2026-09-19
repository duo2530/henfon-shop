import React from 'react';
import { ChevronLeft, ChevronRight, Heart, ShoppingBag, Trash2 } from 'lucide-react';
import { Product } from '../types/ecommerce';

interface WishlistPageProps {
  products: Product[];
  /** 返回商城首页：整页视图不占侧边导航，靠这条回主页。 */
  onBackToHome: () => void;
  onAddToCart: (product: Product) => void;
  onRemoveWishlist: (productId: string) => void;
  /** 点击商品图或标题，打开商品详情。 */
  onQuickView: (product: Product) => void;
}

/**
 * 门户「我的心愿收藏」整页。
 *
 * 内容与旧弹层一致，两点不同：一是占满主内容区，改成响应式卡片网格 —— 弹层里是一列
 * 横向长条（56px 缩略图 + 一行文字），铺到整页宽度上会显得空；二是去掉弹窗外壳与滚动锁，
 * 由页面自己滚动。
 */
export const WishlistPage: React.FC<WishlistPageProps> = ({
  products,
  onBackToHome,
  onAddToCart,
  onRemoveWishlist,
  onQuickView,
}) => (
  <div className="space-y-5">
    <nav aria-label="面包屑" className="flex items-center gap-1.5 text-xs text-zinc-500">
      <button type="button" onClick={onBackToHome} className="transition hover:text-zinc-900">
        商城首页
      </button>
      <ChevronRight className="h-3.5 w-3.5 text-zinc-300" />
      <span className="font-semibold text-zinc-900">我的心愿收藏</span>
    </nav>

    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-bold text-zinc-900">我的心愿收藏</h1>
        <p className="mt-1 text-xs text-zinc-500">共收藏 {products.length} 件心仪好物</p>
      </div>
      <button
        type="button"
        onClick={onBackToHome}
        className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs font-semibold text-zinc-600 transition hover:border-zinc-300 hover:text-zinc-900"
      >
        <ChevronLeft className="h-4 w-4" />
        返回首页继续逛
      </button>
    </div>

    {products.length === 0 ? (
      <div className="rounded-2xl border border-zinc-200/90 bg-white py-20 text-center text-zinc-400 shadow-xs">
        <Heart className="mx-auto h-12 w-12 stroke-1 text-zinc-300" />
        <p className="mt-3 text-sm font-semibold text-zinc-700">暂无收藏的商品</p>
        <p className="mt-1 text-xs text-zinc-400">点击商品卡片右上角的爱心图标即可快速加入心愿单</p>
      </div>
    ) : (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {products.map((product) => (
          <div
            key={product.id}
            className="flex flex-col overflow-hidden rounded-2xl border border-zinc-200/90 bg-white shadow-xs transition hover:border-zinc-300"
          >
            <button
              type="button"
              onClick={() => onQuickView(product)}
              className="group block w-full text-left"
              aria-label={`查看 ${product.title}`}
            >
              <div className="relative aspect-square overflow-hidden bg-zinc-100">
                {product.images[0] ? (
                  <img
                    src={product.images[0]}
                    alt={product.title}
                    className="h-full w-full object-cover transition duration-300 group-hover:scale-105"
                  />
                ) : (
                  <div className="flex h-full w-full items-center justify-center text-2xl text-zinc-300" aria-hidden="true">
                    ▧
                  </div>
                )}
              </div>
            </button>

            <div className="flex flex-1 flex-col gap-2 p-4">
              <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-400">{product.brand}</span>
              <button
                type="button"
                onClick={() => onQuickView(product)}
                className="line-clamp-2 text-left text-sm font-semibold text-zinc-900 transition hover:text-zinc-600"
              >
                {product.title}
              </button>
              <div className="mt-auto flex items-baseline gap-1.5 pt-1">
                <span className="text-base font-bold text-zinc-900">¥{product.price}</span>
                {product.originalPrice > product.price && (
                  <span className="text-xs text-zinc-400 line-through">¥{product.originalPrice}</span>
                )}
              </div>

              <div className="flex items-center gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => onAddToCart(product)}
                  className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-zinc-900 px-3 py-2 text-xs font-semibold text-white transition hover:bg-zinc-800"
                >
                  <ShoppingBag className="h-3.5 w-3.5" />
                  移入购物车
                </button>
                <button
                  type="button"
                  onClick={() => onRemoveWishlist(product.id)}
                  className="rounded-xl p-2 text-zinc-400 transition hover:bg-rose-50 hover:text-rose-500"
                  aria-label={`移除收藏 ${product.title}`}
                  title="移除收藏"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    )}
  </div>
);
