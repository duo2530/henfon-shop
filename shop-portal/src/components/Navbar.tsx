import React, { useState, useRef, useEffect, useMemo } from 'react';
import {
  ShoppingBag,
  Heart,
  Search,
  Package,
  Sparkles,
  SlidersHorizontal,
  X,
  Truck,
  ShieldCheck,
  RotateCcw,
  User,
  Crown,
  LogOut,
  ChevronDown,
  Coins,
  Wallet,
  Settings,
  Gift,
  Ticket,
  Smartphone,
  Headphones,
  Home,
  Shirt,
  Compass,
  Coffee,
  ArrowRight,
  TrendingUp,
  Clock,
  Trash2,
  Star,
  Tag,
  ChevronRight,
} from 'lucide-react';
import { Product, UserProfile } from '../types/ecommerce';
import { ALL_CATEGORY_ID, PortalCategoryNode } from '../api/portalApi';
import { AuthMode } from './AuthModal';

interface NavbarProps {
  cartCount: number;
  cartTotal: number;
  wishlistCount: number;
  ordersCount: number;
  searchQuery: string;
  selectedCategory: string;
  /** 后端下发的类目树，门户导航与搜索建议都以此为唯一来源。 */
  categoryTree: PortalCategoryNode[];
  currentUser: UserProfile | null;
  claimedCouponsCount?: number;
  products?: Product[];
  onSearchChange: (query: string) => void;
  onCategorySelect: (categoryId: string) => void;
  onSelectProduct?: (product: Product) => void;
  onOpenCart: () => void;
  onOpenWishlist: () => void;
  onOpenOrders: () => void;
  onOpenAuth: (mode?: AuthMode) => void;
  onOpenUserProfile: () => void;
  onOpenCouponCenter?: () => void;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  cartCount,
  cartTotal,
  wishlistCount,
  ordersCount,
  searchQuery,
  selectedCategory,
  categoryTree,
  currentUser,
  claimedCouponsCount = 0,
  products = [],
  onSearchChange,
  onCategorySelect,
  onSelectProduct,
  onOpenCart,
  onOpenWishlist,
  onOpenOrders,
  onOpenAuth,
  onOpenUserProfile,
  onOpenCouponCenter,
  onLogout,
}) => {
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const [activeSuggestionIndex, setActiveSuggestionIndex] = useState(-1);
  const [recentSearches, setRecentSearches] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem('aurora_recent_searches');
      return saved ? JSON.parse(saved) : ['降噪耳机', '机械键盘', '咖啡壶'];
    } catch {
      return ['降噪耳机', '机械键盘', '咖啡壶'];
    }
  });

  const userMenuRef = useRef<HTMLDivElement>(null);
  const searchContainerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);
  // 展开二级/三级类目面板的一级类目 ID。
  const [openCategoryId, setOpenCategoryId] = useState<string | null>(null);

  const hotKeywords = ['降噪耳机', '机械键盘', '手冲咖啡壶', '4K显示器', '铝镁合金箱', '羊绒卫衣'];

  // Save search to recent history
  const saveRecentSearch = (query: string) => {
    const trimmed = query.trim();
    if (!trimmed) return;
    setRecentSearches((prev) => {
      const updated = [trimmed, ...prev.filter((item) => item.toLowerCase() !== trimmed.toLowerCase())].slice(0, 8);
      try {
        localStorage.setItem('aurora_recent_searches', JSON.stringify(updated));
      } catch (e) {
        console.error(e);
      }
      return updated;
    });
  };

  const clearRecentSearches = () => {
    setRecentSearches([]);
    try {
      localStorage.removeItem('aurora_recent_searches');
    } catch (e) {
      console.error(e);
    }
  };

  // Close search dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (searchContainerRef.current && !searchContainerRef.current.contains(e.target as Node)) {
        setIsSearchFocused(false);
      }
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setIsUserMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // 类目树按层级深度优先展开，搜索建议需要同时覆盖一级、二级与三级类目。
  const flatCategories = useMemo(() => {
    const result: PortalCategoryNode[] = [];
    const walk = (nodes: PortalCategoryNode[]) => {
      nodes.forEach((node) => {
        result.push(node);
        walk(node.children);
      });
    };
    walk(categoryTree);
    return result;
  }, [categoryTree]);

  // 当前展开下级面板的一级类目。
  const activeNavCategory = useMemo(
    () => (openCategoryId ? categoryTree.find((node) => node.id === openCategoryId) : undefined),
    [openCategoryId, categoryTree]
  );

  // Compute Auto-Suggest Matching Categories & Products
  const { matchingCategories, matchingProducts, totalMatchingCount } = useMemo(() => {
    const trimmed = searchQuery.trim().toLowerCase();
    if (!trimmed) {
      return { matchingCategories: [], matchingProducts: [], totalMatchingCount: 0 };
    }

    // Matching Categories
    const matchedCats = flatCategories.filter((cat) => {
      return cat.name.toLowerCase().includes(trimmed) || cat.code.toLowerCase().includes(trimmed);
    }).map((cat) => {
      // 商品卡片上的分类标签取自叶子类目名，统计时要把该类目的整棵子树都算进来。
      const names = new Set<string>();
      const collect = (node: PortalCategoryNode) => {
        names.add(node.name);
        node.children.forEach(collect);
      };
      collect(cat);
      const count = products.filter((p) => names.has(p.categoryLabel || '')).length;
      return { ...cat, productCount: count };
    }).slice(0, 12);

    // Matching Products
    const allMatches = products.filter((p) => {
      const titleMatch = p.title.toLowerCase().includes(trimmed);
      const subtitleMatch = p.subtitle?.toLowerCase().includes(trimmed);
      const brandMatch = p.brand?.toLowerCase().includes(trimmed);
      const categoryMatch = p.categoryLabel?.toLowerCase().includes(trimmed);
      const tagsMatch = p.features?.some((f) => f.toLowerCase().includes(trimmed));
      return titleMatch || subtitleMatch || brandMatch || categoryMatch || tagsMatch;
    });

    return {
      matchingCategories: matchedCats,
      matchingProducts: allMatches.slice(0, 5),
      totalMatchingCount: allMatches.length,
    };
  }, [searchQuery, products]);

  // Combined suggestions list for keyboard navigation
  const allNavigableItems = useMemo(() => {
    if (!searchQuery.trim()) return [];
    const items: Array<
      | { type: 'category'; data: PortalCategoryNode & { productCount: number } }
      | { type: 'product'; data: Product }
      | { type: 'search_action'; query: string }
    > = [];

    matchingCategories.forEach((c) => items.push({ type: 'category', data: c }));
    matchingProducts.forEach((p) => items.push({ type: 'product', data: p }));
    items.push({ type: 'search_action', query: searchQuery });
    return items;
  }, [searchQuery, matchingCategories, matchingProducts]);

  // Helper to render category icon：后端类目没有图标字段，按类目编码前缀取同一套线性图标。
  const renderCategoryIcon = (categoryCode: string) => {
    const code = (categoryCode || '').toUpperCase();
    if (code.startsWith('ELEC')) return <Smartphone className="w-3.5 h-3.5" />;
    if (code.startsWith('CLOTH')) return <Shirt className="w-3.5 h-3.5" />;
    if (code.startsWith('HOME')) return <Home className="w-3.5 h-3.5" />;
    if (code.startsWith('BEAUTY')) return <Sparkles className="w-3.5 h-3.5" />;
    if (code.startsWith('FOOD') || code.startsWith('LIFE')) return <Coffee className="w-3.5 h-3.5" />;
    if (code.startsWith('AUDIO')) return <Headphones className="w-3.5 h-3.5" />;
    if (code.startsWith('OUTDOOR')) return <Compass className="w-3.5 h-3.5" />;
    return <Tag className="w-3.5 h-3.5" />;
  };

  // Helper to highlight matching text
  const highlightText = (text: string, query: string) => {
    if (!query.trim()) return text;
    const cleanQuery = query.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`(${cleanQuery})`, 'gi');
    const parts = text.split(regex);

    return (
      <span>
        {parts.map((part, i) =>
          part.toLowerCase() === query.trim().toLowerCase() ? (
            <mark key={i} className="bg-amber-200/80 text-zinc-950 font-bold px-0.5 rounded text-inherit">
              {part}
            </mark>
          ) : (
            part
          )
        )}
      </span>
    );
  };

  // Keyboard navigation handler
  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!isSearchFocused) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveSuggestionIndex((prev) => (prev < allNavigableItems.length - 1 ? prev + 1 : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveSuggestionIndex((prev) => (prev > 0 ? prev - 1 : allNavigableItems.length - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (activeSuggestionIndex >= 0 && activeSuggestionIndex < allNavigableItems.length) {
        const item = allNavigableItems[activeSuggestionIndex];
        if (item.type === 'category') {
          handleSelectCategory(item.data.id);
        } else if (item.type === 'product') {
          handleSelectProduct(item.data);
        } else if (item.type === 'search_action') {
          handleExecuteSearch(searchQuery);
        }
      } else {
        handleExecuteSearch(searchQuery);
      }
    } else if (e.key === 'Escape') {
      setIsSearchFocused(false);
      searchInputRef.current?.blur();
    }
  };

  const handleSelectCategory = (categoryId: string) => {
    onCategorySelect(categoryId);
    setOpenCategoryId(null);
    setIsSearchFocused(false);
    const name = categoryId === ALL_CATEGORY_ID
      ? '全部商品'
      : flatCategories.find((item) => item.id === categoryId)?.name;
    saveRecentSearch(name || searchQuery || categoryId);
  };

  const handleSelectProduct = (product: Product) => {
    if (onSelectProduct) {
      onSelectProduct(product);
    }
    saveRecentSearch(product.title);
    setIsSearchFocused(false);
  };

  const handleExecuteSearch = (query: string) => {
    if (query.trim()) {
      saveRecentSearch(query);
    }
    onSearchChange(query);
    setIsSearchFocused(false);
    searchInputRef.current?.blur();
  };

  return (
    <header className="sticky top-0 z-30 bg-white border-b border-zinc-200/80 shadow-xs">
      {/* Top Notification Announcement Bar */}
      <div className="bg-zinc-900 text-zinc-300 text-xs py-1.5 px-4">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 text-amber-400 font-medium bg-zinc-800 px-2 py-0.5 rounded text-[11px]">
              <Sparkles className="w-3 h-3" />
              {currentUser ? `${currentUser.memberLevel}专属礼遇` : '开业特惠'}
            </span>
            <span>
              {currentUser ? (
                <>
                  尊贵的 <strong>{currentUser.nickname}</strong>，您享有全场专享特惠与优先配送服务
                </>
              ) : (
                <>
                  全场实付满 ¥99 享免费配送 | 新用户注册即送 <strong>¥100</strong> 优惠券礼包
                </>
              )}
            </span>
          </div>

          <div className="hidden md:flex items-center gap-4 text-[11px] text-zinc-400">
            <span className="flex items-center gap-1">
              <Truck className="w-3 h-3 text-emerald-400" /> 极速配送
            </span>
            <span className="flex items-center gap-1">
              <ShieldCheck className="w-3 h-3 text-emerald-400" /> 100% 正品行货
            </span>
            <span className="flex items-center gap-1">
              <RotateCcw className="w-3 h-3 text-emerald-400" /> 7天无理由退换
            </span>
            {!currentUser ? (
              <button
                onClick={() => onOpenAuth('register')}
                className="text-amber-400 hover:text-amber-300 font-bold ml-2 underline underline-offset-2 transition"
              >
                免费注册领券
              </button>
            ) : null}
          </div>
        </div>
      </div>

      {/* Main Header Row */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3.5">
        <div className="flex items-center justify-between gap-3 md:gap-8">
          {/* Brand Logo */}
          <div
            onClick={() => {
              onCategorySelect('all');
              onSearchChange('');
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onCategorySelect('all');
                onSearchChange('');
              }
            }}
            role="button"
            tabIndex={0}
            aria-label="返回商城首页"
            className="flex items-center gap-2.5 cursor-pointer select-none shrink-0"
          >
            <div className="w-10 h-10 rounded-xl bg-zinc-900 flex items-center justify-center text-white shadow-sm font-bold text-lg tracking-wider">
              A
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="text-lg font-bold tracking-tight text-zinc-900">Henfon商城</span>
                <span className="text-xs px-1.5 py-0.5 rounded bg-zinc-100 text-zinc-700 font-semibold uppercase">
                  STORE
                </span>
              </div>
              <p className="text-[11px] text-zinc-400 font-medium hidden sm:block">
                品质生活精选
              </p>
            </div>
          </div>

          {/* Search Bar with Auto-Suggest Dropdown */}
          <div className="flex-1 max-w-xl relative" ref={searchContainerRef}>
            <div
              className={`relative flex items-center w-full rounded-xl border transition-all ${
                isSearchFocused
                  ? 'border-zinc-900 ring-2 ring-zinc-900/10 bg-white'
                  : 'border-zinc-200 bg-zinc-50/80 hover:bg-zinc-50'
              }`}
            >
              <Search className="w-4 h-4 text-zinc-400 ml-3.5 shrink-0" />
              <input
                ref={searchInputRef}
                type="text"
                aria-label="搜索商品、品牌或型号"
                aria-autocomplete="list"
                aria-expanded={isSearchFocused}
                placeholder="搜索精选好物、品牌或型号..."
                value={searchQuery}
                onFocus={() => {
                  setIsSearchFocused(true);
                  setActiveSuggestionIndex(-1);
                }}
                onKeyDown={handleKeyDown}
                onChange={(e) => {
                  onSearchChange(e.target.value);
                  setActiveSuggestionIndex(-1);
                }}
                className="w-full py-2.5 pl-2.5 pr-8 bg-transparent text-sm text-zinc-900 placeholder:text-zinc-400 focus:outline-none"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => {
                    onSearchChange('');
                    searchInputRef.current?.focus();
                  }}
                  className="absolute right-3 p-1 rounded-full text-zinc-400 hover:text-zinc-700 transition"
                  title="清除搜索内容"
                  aria-label="清除搜索内容"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>

            {/* Auto-Suggest Dropdown */}
            {isSearchFocused && (
              <div className="absolute top-full left-0 right-0 mt-2 rounded-2xl bg-white/95 backdrop-blur-md border border-zinc-200 shadow-2xl z-50 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-150 max-h-[480px] overflow-y-auto">
                {/* 1. When query is empty: Show Recent Searches, Hot Keywords, Quick Categories */}
                {!searchQuery.trim() ? (
                  <div className="p-4 space-y-4">
                    {/* Recent Searches */}
                    {recentSearches.length > 0 && (
                      <div>
                        <div className="flex items-center justify-between text-xs font-semibold text-zinc-500 mb-2">
                          <span className="flex items-center gap-1.5">
                            <Clock className="w-3.5 h-3.5 text-zinc-400" />
                            最近搜索
                          </span>
                          <button
                            type="button"
                            onClick={clearRecentSearches}
                            className="text-[11px] text-zinc-400 hover:text-rose-500 flex items-center gap-1 transition"
                          >
                            <Trash2 className="w-3 h-3" />
                            清空
                          </button>
                        </div>
                        <div className="flex flex-wrap gap-1.5">
                          {recentSearches.map((item) => (
                            <button
                              key={item}
                              type="button"
                              onClick={() => handleExecuteSearch(item)}
                              className="text-xs px-2.5 py-1 rounded-lg bg-zinc-100 hover:bg-zinc-200 text-zinc-700 transition flex items-center gap-1"
                            >
                              <span>{item}</span>
                            </button>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Hot Keywords */}
                    <div>
                      <div className="flex items-center gap-1.5 text-xs font-semibold text-zinc-500 mb-2">
                        <TrendingUp className="w-3.5 h-3.5 text-amber-500" />
                        热门好物搜索
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {hotKeywords.map((kw, idx) => (
                          <button
                            key={kw}
                            type="button"
                            onClick={() => handleExecuteSearch(kw)}
                            className="text-xs px-2.5 py-1 rounded-lg bg-zinc-100 hover:bg-amber-50 hover:text-amber-900 hover:border-amber-200 border border-transparent text-zinc-700 transition flex items-center gap-1.5"
                          >
                            <span
                              className={`text-[10px] font-bold px-1 rounded ${
                                idx < 3 ? 'bg-amber-400 text-zinc-950' : 'bg-zinc-200 text-zinc-600'
                              }`}
                            >
                              {idx + 1}
                            </span>
                            <span>{kw}</span>
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Quick Category Jump */}
                    <div className="pt-2 border-t border-zinc-100">
                      <div className="text-[11px] font-semibold text-zinc-400 mb-2">按分类快速发现</div>
                      <div className="grid grid-cols-3 sm:grid-cols-6 gap-1.5">
                        {categoryTree.map((cat) => (
                          <button
                            key={cat.id}
                            type="button"
                            onClick={() => handleSelectCategory(cat.id)}
                            className="p-2 rounded-xl bg-zinc-50 hover:bg-zinc-100 border border-zinc-100 text-center flex flex-col items-center gap-1 transition text-zinc-700 hover:text-zinc-900"
                          >
                            <div className="w-7 h-7 rounded-lg bg-white shadow-xs flex items-center justify-center text-zinc-700">
                              {renderCategoryIcon(cat.code)}
                            </div>
                            <span className="text-[11px] font-medium">{cat.name}</span>
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>
                ) : (
                  /* 2. When query is present: Auto-Suggest Matching Categories and Matching Products */
                  <div>
                    {/* (A) Matching Categories Section */}
                    {matchingCategories.length > 0 && (
                      <div className="p-3 border-b border-zinc-100 bg-zinc-50/50">
                        <div className="flex items-center gap-1.5 text-[11px] font-bold text-zinc-400 uppercase tracking-wider mb-2 px-1">
                          <Tag className="w-3 h-3 text-zinc-500" />
                          匹配分类
                        </div>
                        <div className="flex flex-wrap gap-2">
                          {matchingCategories.map((cat) => (
                            <button
                              key={cat.id}
                              type="button"
                              onClick={() => handleSelectCategory(cat.id)}
                              className="group px-3 py-1.5 rounded-xl bg-white hover:bg-zinc-900 hover:text-white border border-zinc-200 text-zinc-800 text-xs font-medium transition flex items-center gap-2 shadow-xs"
                            >
                              <div className="text-zinc-500 group-hover:text-amber-400 transition">
                                {renderCategoryIcon(cat.code)}
                              </div>
                              <span>
                                {highlightText(cat.name, searchQuery)}
                              </span>
                              <span className="text-[10px] text-zinc-400 group-hover:text-zinc-500">
                                {cat.level === 1 ? '一级' : cat.level === 2 ? '二级' : '三级'}
                              </span>
                              {cat.productCount > 0 && (
                                <span className="text-[10px] text-zinc-400 group-hover:text-zinc-300">
                                  （本页 {cat.productCount} 件）
                                </span>
                              )}
                              <ChevronRight className="w-3 h-3 text-zinc-400 group-hover:text-white transition" />
                            </button>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* (B) Matching Products Section */}
                    {matchingProducts.length > 0 ? (
                      <div className="p-2">
                        <div className="flex items-center justify-between px-2 py-1.5 text-[11px] font-bold text-zinc-400 uppercase tracking-wider">
                          <span className="flex items-center gap-1.5">
                            <Sparkles className="w-3 h-3 text-amber-500" />
                            匹配商品推荐 ({totalMatchingCount} 款)
                          </span>
                          <span className="text-[10px] text-zinc-400 font-normal">点击直接预览</span>
                        </div>

                        <div className="space-y-1 mt-1">
                          {matchingProducts.map((product) => (
                            <div
                              key={product.id}
                              onClick={() => handleSelectProduct(product)}
                              className="w-full p-2 rounded-xl hover:bg-zinc-100/90 cursor-pointer flex items-center justify-between gap-3 transition group"
                            >
                              <div className="flex items-center gap-3 min-w-0">
                                {/* Thumbnail */}
                                <div className="w-11 h-11 rounded-lg overflow-hidden bg-zinc-100 shrink-0 border border-zinc-200/80 group-hover:border-zinc-300 transition">
                                  <img
                                    src={product.images[0]}
                                    alt={product.title}
                                    className="w-full h-full object-cover group-hover:scale-105 transition duration-200"
                                  />
                                </div>

                                {/* Title, Brand & Category */}
                                <div className="min-w-0 flex-1 text-left">
                                  <div className="flex items-center gap-1.5">
                                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-zinc-100 text-zinc-600 font-semibold group-hover:bg-zinc-200 transition">
                                      {product.categoryLabel}
                                    </span>
                                    {product.badge && (
                                      <span className="text-[10px] px-1.5 py-0.2 rounded bg-amber-100 text-amber-900 font-bold">
                                        {product.badge}
                                      </span>
                                    )}
                                    <span className="text-[11px] text-zinc-400 truncate">
                                      {product.brand}
                                    </span>
                                  </div>
                                  <p className="text-xs font-semibold text-zinc-900 truncate mt-0.5 group-hover:text-amber-700 transition">
                                    {highlightText(product.title, searchQuery)}
                                  </p>
                                </div>
                              </div>

                              {/* Price & Rating */}
                              <div className="shrink-0 text-right">
                                <div className="flex items-center justify-end gap-1 text-xs font-bold text-zinc-900">
                                  <span>¥{product.price}</span>
                                  {product.originalPrice > product.price && (
                                    <span className="text-[10px] text-zinc-400 line-through font-normal">
                                      ¥{product.originalPrice}
                                    </span>
                                  )}
                                </div>
                                <div className="flex items-center justify-end gap-1 mt-0.5 text-[10px] text-zinc-400">
                                  {product.reviewCount > 0 ? (
                                    <>
                                      <Star className="w-2.5 h-2.5 fill-amber-400 text-amber-400" />
                                      <span>{product.rating}</span>
                                    </>
                                  ) : (
                                    <span>暂无评价</span>
                                  )}
                                  <span>·</span>
                                  <span>已售{product.salesCount}</span>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : (
                      /* No Products matched */
                      <div className="p-6 text-center">
                        <div className="w-10 h-10 rounded-full bg-zinc-100 text-zinc-400 flex items-center justify-center mx-auto mb-2">
                          <Search className="w-5 h-5" />
                        </div>
                        <p className="text-xs font-bold text-zinc-700">
                          未找到包含「{searchQuery}」的商品
                        </p>
                        <p className="text-[11px] text-zinc-400 mt-1">
                          请尝试缩短关键词，或点击下方分类进行浏览
                        </p>
                        <div className="flex flex-wrap justify-center gap-1.5 mt-3">
                          {categoryTree.map((cat) => (
                            <button
                              key={cat.id}
                              type="button"
                              onClick={() => handleSelectCategory(cat.id)}
                              className="text-xs px-2.5 py-1 rounded-lg bg-zinc-100 hover:bg-zinc-200 text-zinc-700 transition"
                            >
                              {cat.name}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* (C) Footer: Full Search Execution Action */}
                    <div className="p-2.5 border-t border-zinc-100 bg-zinc-50 flex items-center justify-between text-xs">
                      <button
                        type="button"
                        onClick={() => handleExecuteSearch(searchQuery)}
                        className="w-full py-2 px-3 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-white font-semibold flex items-center justify-between transition shadow-xs"
                      >
                        <span className="flex items-center gap-1.5 truncate">
                          <Search className="w-3.5 h-3.5 text-zinc-300" />
                          <span>
                            查看「<strong className="text-amber-400 font-bold">{searchQuery}</strong>」的全部搜索结果
                          </span>
                        </span>
                        <span className="flex items-center gap-1 text-xs text-zinc-300 shrink-0">
                          <span>{totalMatchingCount} 件商品</span>
                          <ArrowRight className="w-3.5 h-3.5" />
                        </span>
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* User Actions */}
          <div className="flex items-center gap-1.5 sm:gap-2.5 shrink-0">
            {/* Orders History Button */}
            <button
              onClick={onOpenOrders}
              aria-label={`打开我的订单${ordersCount > 0 ? `，${ordersCount} 个待处理订单` : ''}`}
              className="relative p-2 sm:px-3 sm:py-2 rounded-xl text-zinc-700 hover:bg-zinc-100 transition flex items-center gap-1.5 text-xs font-medium"
              title="我的订单"
            >
              <Package className="w-4 h-4" />
              <span className="hidden sm:inline">订单</span>
              {ordersCount > 0 && <span className="w-2 h-2 rounded-full bg-emerald-500"></span>}
            </button>

            {/* Wishlist Button */}
            <button
              onClick={onOpenWishlist}
              aria-label={`打开收藏夹${wishlistCount > 0 ? `，${wishlistCount} 件商品` : ''}`}
              className="relative p-2 sm:px-3 sm:py-2 rounded-xl text-zinc-700 hover:bg-zinc-100 transition flex items-center gap-1.5 text-xs font-medium"
              title="收藏夹"
            >
              <Heart className="w-4 h-4" />
              <span className="hidden sm:inline">收藏</span>
              {wishlistCount > 0 && (
                <span className="px-1.5 py-0.2 rounded-full bg-rose-500 text-white text-[10px] font-bold">
                  {wishlistCount}
                </span>
              )}
            </button>

            {/* Cart Drawer Trigger */}
            <button
              onClick={onOpenCart}
              aria-label={`打开购物车，共 ${cartCount} 件商品，合计 ¥${cartTotal.toFixed(2)}`}
              className="relative flex items-center gap-2.5 px-3 sm:px-3.5 py-2 rounded-xl bg-zinc-900 text-white hover:bg-zinc-800 transition shadow-xs"
              title="购物车"
            >
              <div className="relative">
                <ShoppingBag className="w-4 h-4" />
                {cartCount > 0 && (
                  <span className="absolute -top-2 -right-2 w-4 h-4 rounded-full bg-amber-400 text-zinc-950 text-[10px] font-black flex items-center justify-center">
                    {cartCount > 99 ? '99+' : cartCount}
                  </span>
                )}
              </div>
              <div className="hidden sm:flex flex-col text-left">
                <span className="text-[10px] text-zinc-400 leading-none">购物车</span>
                <span className="text-xs font-bold leading-tight">¥{cartTotal.toLocaleString()}</span>
              </div>
            </button>

            {/* User Login/Profile Action Button */}
            {currentUser ? (
              /* Logged-In User Profile Menu Trigger */
              <div className="relative" ref={userMenuRef}>
                <button
                  onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                  aria-label="打开用户中心"
                  aria-expanded={isUserMenuOpen}
                  aria-haspopup="menu"
                  className="flex items-center gap-2 pl-1.5 pr-2.5 py-1.5 rounded-2xl bg-zinc-100 hover:bg-zinc-200/80 border border-zinc-200 transition select-none"
                  title="用户中心"
                >
                  <div className="relative">
                    <img
                      src={currentUser.avatar}
                      alt={currentUser.nickname}
                      className="w-7 h-7 rounded-xl object-cover ring-1 ring-zinc-300 bg-zinc-800"
                    />
                    {currentUser.memberLevel === '黑金SVIP' && (
                      <div className="absolute -bottom-1 -right-1 w-3.5 h-3.5 rounded-full bg-zinc-900 text-amber-400 flex items-center justify-center text-[8px] font-bold">
                        <Crown className="w-2.5 h-2.5" />
                      </div>
                    )}
                  </div>
                  <div className="hidden md:flex flex-col text-left">
                    <span className="text-xs font-bold text-zinc-900 leading-tight max-w-[80px] truncate">
                      {currentUser.nickname}
                    </span>
                    <span className="text-[10px] font-semibold text-amber-600 leading-none">
                      {currentUser.memberLevel}
                    </span>
                  </div>
                  <ChevronDown className="w-3.5 h-3.5 text-zinc-500" />
                </button>

                {/* User Dropdown Menu */}
                {isUserMenuOpen && (
                  <div className="absolute right-0 top-full mt-2 w-64 bg-white rounded-3xl shadow-2xl border border-zinc-200 p-3 z-50 animate-in fade-in slide-in-from-top-2 duration-150">
                    {/* Header Card */}
                    <div
                      onClick={() => {
                        setIsUserMenuOpen(false);
                        onOpenUserProfile();
                      }}
                      className="p-3 rounded-2xl bg-zinc-900 text-white cursor-pointer hover:bg-zinc-800 transition relative overflow-hidden"
                    >
                      <div className="flex items-center gap-3">
                        <img
                          src={currentUser.avatar}
                          alt={currentUser.nickname}
                          className="w-10 h-10 rounded-xl object-cover ring-1 ring-amber-400/50"
                        />
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-bold truncate">{currentUser.nickname}</p>
                          <div className="flex items-center gap-1.5 mt-0.5">
                            <span className="text-[10px] px-1.5 py-0.2 rounded bg-amber-400 text-zinc-950 font-bold">
                              {currentUser.memberLevel}
                            </span>
                            <span className="text-[10px] text-zinc-400">查看主页 →</span>
                          </div>
                        </div>
                      </div>

                      {/* Assets row */}
                      <div className="grid grid-cols-2 gap-2 mt-3 pt-2.5 border-t border-zinc-800 text-[11px]">
                        <div>
                          <span className="text-zinc-400 text-[10px]">积分</span>
                          <p className="font-bold text-amber-400">{currentUser.points} P</p>
                        </div>
                        <div>
                          <span className="text-zinc-400 text-[10px]">钱包余额</span>
                          <p className="font-bold text-white">¥{currentUser.balance.toFixed(2)}</p>
                        </div>
                      </div>
                    </div>

                    {/* Navigation list */}
                    <div className="space-y-1 mt-2 text-xs font-medium text-zinc-700">
                      <button
                        onClick={() => {
                          setIsUserMenuOpen(false);
                          onOpenUserProfile();
                        }}
                        className="w-full px-3 py-2 rounded-xl hover:bg-zinc-100 flex items-center justify-between transition"
                      >
                        <span className="flex items-center gap-2">
                          <User className="w-4 h-4 text-zinc-500" />
                          个人资料与特权
                        </span>
                        <span className="text-[10px] text-zinc-400">编辑</span>
                      </button>

                      <button
                        onClick={() => {
                          setIsUserMenuOpen(false);
                          onOpenOrders();
                        }}
                        className="w-full px-3 py-2 rounded-xl hover:bg-zinc-100 flex items-center justify-between transition"
                      >
                        <span className="flex items-center gap-2">
                          <Package className="w-4 h-4 text-zinc-500" />
                          我的订单
                        </span>
                        {ordersCount > 0 && (
                          <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-emerald-100 text-emerald-800 font-bold">
                            {ordersCount}
                          </span>
                        )}
                      </button>

                      <button
                        onClick={() => {
                          setIsUserMenuOpen(false);
                          if (onOpenCouponCenter) onOpenCouponCenter();
                        }}
                        className="w-full px-3 py-2 rounded-xl hover:bg-zinc-100 flex items-center justify-between transition"
                      >
                        <span className="flex items-center gap-2">
                          <Ticket className="w-4 h-4 text-amber-600" />
                          领券中心
                        </span>
                        <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-amber-100 text-amber-900 font-bold">
                          {claimedCouponsCount > 0 ? `已领${claimedCouponsCount}张` : '领神券'}
                        </span>
                      </button>

                      <button
                        onClick={() => {
                          setIsUserMenuOpen(false);
                          onOpenWishlist();
                        }}
                        className="w-full px-3 py-2 rounded-xl hover:bg-zinc-100 flex items-center justify-between transition"
                      >
                        <span className="flex items-center gap-2">
                          <Heart className="w-4 h-4 text-zinc-500" />
                          收藏夹
                        </span>
                        {wishlistCount > 0 && (
                          <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-rose-100 text-rose-800 font-bold">
                            {wishlistCount}
                          </span>
                        )}
                      </button>
                    </div>

                    {/* Logout Button */}
                    <div className="mt-2 pt-2 border-t border-zinc-100">
                      <button
                        onClick={() => {
                          setIsUserMenuOpen(false);
                          onLogout();
                        }}
                        className="w-full px-3 py-2 rounded-xl text-rose-600 hover:bg-rose-50 text-xs font-semibold flex items-center gap-2 transition"
                      >
                        <LogOut className="w-4 h-4" />
                        <span>退出登录</span>
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* Logged-Out Login / Register Trigger Button */
              <div className="flex items-center gap-1.5">
                <button
                  onClick={() => onOpenAuth('login-pwd')}
                  className="px-3 sm:px-3.5 py-2 rounded-xl text-xs font-bold bg-amber-400 hover:bg-amber-300 text-zinc-950 transition flex items-center gap-1.5 shadow-sm active:scale-95"
                >
                  <User className="w-3.5 h-3.5" />
                  <span>登录 / 注册</span>
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Category Navigation Pills & Coupon Center Shortcut */}
        <div className="relative pt-3 mt-1" onMouseLeave={() => setOpenCategoryId(null)}>
          <div className="flex items-center justify-between gap-2">
            <nav className="flex items-center gap-2 overflow-x-auto no-scrollbar flex-1">
              <button
                onClick={() => handleSelectCategory(ALL_CATEGORY_ID)}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all ${
                  selectedCategory === ALL_CATEGORY_ID
                    ? 'bg-zinc-900 text-white shadow-xs'
                    : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200/80 hover:text-zinc-900'
                }`}
              >
                全部商品
              </button>
              {categoryTree.map((cat) => {
                const isActive = selectedCategory === cat.id;
                const isOpen = openCategoryId === cat.id;
                const hasChildren = cat.children.length > 0;
                return (
                  <button
                    key={cat.id}
                    onMouseEnter={() => setOpenCategoryId(hasChildren ? cat.id : null)}
                    onFocus={() => setOpenCategoryId(hasChildren ? cat.id : null)}
                    onClick={() => {
                      // 触屏没有 hover，点击时在选中类目的同时展开下级面板。
                      handleSelectCategory(cat.id);
                      setOpenCategoryId(hasChildren ? cat.id : null);
                    }}
                    aria-expanded={hasChildren ? isOpen : undefined}
                    className={`px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all flex items-center gap-1.5 ${
                      isActive || isOpen
                        ? 'bg-zinc-900 text-white shadow-xs'
                        : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200/80 hover:text-zinc-900'
                    }`}
                  >
                    <span>{cat.name}</span>
                    {hasChildren && (
                      <ChevronDown className={`w-3 h-3 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
                    )}
                  </button>
                );
              })}
            </nav>

            {/* Quick Coupon Center Header Button */}
            {onOpenCouponCenter && (
              <button
                onClick={onOpenCouponCenter}
                className="shrink-0 px-3 py-1.5 rounded-lg bg-linear-to-r from-amber-500 to-rose-500 text-white text-xs font-bold hover:opacity-95 transition flex items-center gap-1.5 shadow-xs"
              >
                <Ticket className="w-3.5 h-3.5" />
                <span>领券中心</span>
                <span className="text-[10px] bg-white/20 px-1 py-0.2 rounded-full">
                  最高省¥150
                </span>
              </button>
            )}
          </div>

          {/* 二级 / 三级类目面板：挂在导航条下沿，避免被横向滚动容器裁切 */}
          {activeNavCategory && (
            <div className="absolute left-0 right-0 top-full z-30 mt-1 rounded-2xl border border-zinc-200 bg-white p-5 shadow-xl">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                <button
                  onClick={() => handleSelectCategory(activeNavCategory.id)}
                  className="flex items-center gap-2 text-sm font-bold text-zinc-900 transition hover:text-amber-600"
                >
                  {renderCategoryIcon(activeNavCategory.code)}
                  <span>{activeNavCategory.name} 全部商品</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
                <span className="text-[11px] text-zinc-400">
                  共 {activeNavCategory.children.length} 个二级分类
                </span>
              </div>
              <div className="grid grid-cols-1 gap-x-6 gap-y-5 sm:grid-cols-2 lg:grid-cols-4">
                {activeNavCategory.children.map((level2) => (
                  <div key={level2.id} className="min-w-0">
                    <button
                      onClick={() => handleSelectCategory(level2.id)}
                      className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-zinc-900 transition hover:text-amber-600"
                    >
                      <span className="h-3 w-1 rounded-full bg-amber-400" />
                      {level2.name}
                    </button>
                    <div className="flex flex-wrap gap-1.5">
                      {level2.children.map((level3) => (
                        <button
                          key={level3.id}
                          onClick={() => handleSelectCategory(level3.id)}
                          className={`rounded-lg px-2 py-1 text-[11px] transition ${
                            selectedCategory === level3.id
                              ? 'bg-zinc-900 text-white'
                              : 'bg-zinc-50 text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900'
                          }`}
                        >
                          {level3.name}
                        </button>
                      ))}
                      {level2.children.length === 0 && (
                        <span className="text-[11px] text-zinc-400">暂无三级分类</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
