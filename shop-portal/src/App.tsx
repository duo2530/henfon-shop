import React, { useState, useEffect, useMemo } from 'react';
import { Navbar } from './components/Navbar';
import { HeroBanner } from './components/HeroBanner';
import { ProductCard } from './components/ProductCard';
import { ProductQuickView } from './components/ProductQuickView';
import { CartDrawer } from './components/CartDrawer';
import { CheckoutModal, CheckoutPersistenceResult } from './components/CheckoutModal';
import { OrderSuccessModal } from './components/OrderSuccessModal';
import { OrdersModal } from './components/OrdersModal';
import { WishlistModal } from './components/WishlistModal';
import { CompareModal } from './components/CompareModal';
import { CompareFloatingBar } from './components/CompareFloatingBar';
import { AuthModal, AuthMode, PRESET_TEST_USERS } from './components/AuthModal';
import { UserProfileModal } from './components/UserProfileModal';
import { CouponCenter } from './components/CouponCenter';
import { ToastContainer, ToastMessage } from './components/Toast';
import { PRODUCTS, AVAILABLE_COUPONS } from './data/products';
import {
  addPortalCartItem,
  claimPortalCoupon,
  createPortalOrder,
  createPortalPayment,
  fetchPortalPayment,
  closePortalPayment,
  redeemPortalCoupon,
  rollbackPortalCoupon,
  clearPortalMemberToken,
  hasPortalMemberSession,
  deletePortalCartItem,
  fetchPortalAddresses,
  fetchPortalBanners,
  fetchPortalCart,
  fetchPortalCoupons,
  fetchPortalFavorites,
  fetchPortalOrders,
  fetchPortalOrderDetail,
  fetchPortalOrderLogistics,
  applyPortalOrderInvoice,
  fetchPortalOrderInvoice,
  fetchPortalAfterSales,
  createPortalAfterSale,
  cancelPortalAfterSale,
  cancelPortalOrder,
  confirmPortalOrder,
  fetchPortalProductDetail,
  fetchPortalProducts,
  logoutPortalMember,
  savePortalAddress,
  updatePortalAddress,
  deletePortalAddress,
  setDefaultPortalAddress,
  togglePortalFavorite,
  updatePortalCartItem,
  PortalBanner,
  PortalOrderItemRecord,
  PortalOrderLogisticsRecord,
  PortalAfterSaleRecord,
} from './api/portalApi';
import {
  Product,
  CartItem,
  Order,
  Coupon,
  SortOption,
  CompareHistoryItem,
  UserProfile,
} from './types/ecommerce';
import {
  SlidersHorizontal,
  LayoutGrid,
  List,
  Sparkles,
  Search,
  RotateCcw,
  ArrowUpDown,
  Filter,
  Check,
  Package,
  ShieldCheck,
  Truck,
  Heart,
  Scale,
} from 'lucide-react';

const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === 'true';

function resolveMemberId(user: UserProfile | null): number | null {
  if (!user) return null;
  const match = user.id.match(/(\d+)$/);
  return match ? Number(match[1]) : null;
}

function resolveSelectedSku(product: Product, variants: Record<string, string>) {
  return product.skus?.find((sku) =>
    Object.entries(sku.attributes).every(([name, value]) => variants[name] === value)
  );
}

function mapPortalOrderStatus(orderStatus: number): { status: Order['status']; label: string } {
  switch (orderStatus) {
    case 10:
      return { status: 'placed', label: '待付款' };
    case 20:
      return { status: 'processing', label: '待发货（已付款）' };
    case 30:
      return { status: 'shipped', label: '运输中' };
    case 40:
      return { status: 'delivered', label: '已完成' };
    case 50:
      return { status: 'cancelled', label: '已取消' };
    case 60:
      return { status: 'refunding', label: '退款中' };
    case 70:
      return { status: 'refunded', label: '已退款' };
    default:
      return { status: 'processing', label: '处理中' };
  }
}

function formatPortalDate(value?: string): string {
  return value ? value.replace('T', ' ') : '';
}

function mapPortalOrderItem(item: PortalOrderItemRecord, products: Product[]): import('./types/ecommerce').OrderItem {
  const product = item.productId ? products.find((candidate) => candidate.id === `prod-${item.productId}`) : undefined;
  return {
    productId: item.productId ? `prod-${item.productId}` : product?.id || '',
    skuId: item.skuId,
    title: item.productName,
    image: item.imageUrl || product?.images[0] || '',
    variantsSummary: item.skuName || item.skuCode || '默认规格',
    price: Number(item.unitPrice || 0),
    quantity: Number(item.quantity || 0),
  };
}

function mapPortalLogistics(logistics: PortalOrderLogisticsRecord[]): Order['trackingSteps'] {
  return logistics.map((event) => ({
    title: event.eventDescription || event.logisticsStatus || '物流状态更新',
    time: formatPortalDate(event.eventTime),
    completed: true,
    description: event.eventDescription || '',
    location: event.eventLocation,
  }));
}

type PaymentPollingTask = {
  memberId: number;
  paymentNo: string;
  order: Order;
  expiresAt: number;
};

function mapPaymentState(status?: number): Order['paymentState'] {
  if (status === 2) return 'succeeded';
  if (status === 3) return 'expired';
  if (status === 4) return 'failed';
  if (status === 1) return 'processing';
  return 'pending';
}

export default function App() {
  // 1. Persistence & State
  const [products, setProducts] = useState<Product[]>(DEMO_MODE ? PRODUCTS : []);
  const [coupons, setCoupons] = useState<Coupon[]>(DEMO_MODE ? AVAILABLE_COUPONS : []);
  const [banners, setBanners] = useState<PortalBanner[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [sortBy, setSortBy] = useState<SortOption>('featured');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

  // Advanced Filters
  const [onlyInStock, setOnlyInStock] = useState<boolean>(false);
  const [onlyDiscount, setOnlyDiscount] = useState<boolean>(false);
  const [priceRange, setPriceRange] = useState<[number, number]>([0, 2000]);

  // Comparison State
  const [isCompareMode, setIsCompareMode] = useState<boolean>(false);
  const [compareProductIds, setCompareProductIds] = useState<string[]>([]);
  const [isCompareModalOpen, setIsCompareModalOpen] = useState<boolean>(false);
  const [isCompareBarVisible, setIsCompareBarVisible] = useState<boolean>(true);

  // Compare History & Persistence
  const [lastComparedProductIds, setLastComparedProductIds] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem('aurora_last_compare');
      return saved ? JSON.parse(saved) : DEMO_MODE ? ['prod-1', 'prod-2'] : [];
    } catch {
      return DEMO_MODE ? ['prod-1', 'prod-2'] : [];
    }
  });

  const [compareHistory, setCompareHistory] = useState<CompareHistoryItem[]>(() => {
    try {
      const saved = localStorage.getItem('aurora_compare_history');
      return saved
        ? JSON.parse(saved)
        : DEMO_MODE
          ? [
            {
              id: 'comp-init-1',
              timestamp: Date.now() - 1000 * 60 * 15,
              productIds: ['prod-1', 'prod-2'],
            },
          ]
          : [];
    } catch {
      return [];
    }
  });

  // Cart & Orders & Wishlist
  const [cartItems, setCartItems] = useState<CartItem[]>(() => {
    try {
      const saved = localStorage.getItem('aurora_cart');
      const parsed = saved ? JSON.parse(saved) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  });

  const [wishlist, setWishlist] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem('aurora_wishlist');
      const parsed = saved ? JSON.parse(saved) : DEMO_MODE ? ['prod-1'] : [];
      return Array.isArray(parsed) ? parsed : DEMO_MODE ? ['prod-1'] : [];
    } catch {
      return DEMO_MODE ? ['prod-1'] : [];
    }
  });

  const [orders, setOrders] = useState<Order[]>(() => {
    try {
      const saved = localStorage.getItem('aurora_orders');
      const parsed = saved ? JSON.parse(saved) : [];
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    } catch {
      // fallback to initial demo order
    }
    return DEMO_MODE
      ? [
      {
        id: 'ord-preset-001',
        orderNumber: 'ORD-2026-889921',
        trackingNumber: 'SF19837482910',
        createdAt: '2026-08-28 14:32:00',
        status: 'shipped',
        statusLabel: '顺丰速运运输中 (已发货)',
        items: [
          {
            productId: 'p-1',
            title: 'Henfon声学 Pro 无线降噪头戴式耳机',
            image: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&auto=format&fit=crop&q=80',
            variantsSummary: '颜色: 曜石黑',
            price: 1899,
            quantity: 1,
          },
        ],
        subtotal: 1899,
        discount: 200,
        shippingFee: 0,
        totalPaid: 1699,
        shippingAddress: {
          id: 'addr-1',
          receiverName: '李思源',
          phone: '138****8899',
          province: '上海市',
          city: '上海市',
          district: '浦东新区',
          detail: '科技园区张江高科南路 88 号创新大厦 12 层',
          tag: '公司',
          isDefault: true,
        },
        paymentMethod: '微信支付 (安全托管)',
        estimatedDelivery: '预计明日 12:00 前顺丰特快送达',
        trackingSteps: [
          {
            title: '订单提交成功，等待商家处理',
            time: '2026-08-28 14:32:00',
            completed: true,
            description: '订单创建并已完成资金安全托管。',
          },
          {
            title: '华东智能中央仓已完成拣货打包',
            time: '2026-08-28 16:15:30',
            completed: true,
            description: '商品完成多重防震加固与防伪码核验，已出库等待揽收。',
          },
          {
            title: '顺丰速运已揽收，航空干线运输中',
            time: '2026-08-28 18:40:12',
            completed: true,
            description: '快件已通过顺丰特快干线发往目的地分拨中心。',
          },
          {
            title: '目的地营业部派送中',
            time: '预计明日 08:30',
            completed: false,
            description: '顺丰特快专员将进行电话预约并送货上门。',
          },
          {
            title: '包裹妥投签收',
            time: '预计明日中午',
            completed: false,
            description: '本人签收并完成验视。',
          },
        ],
      },
    ]
      : [];
  });
  const [afterSales, setAfterSales] = useState<PortalAfterSaleRecord[]>([]);
  const [afterSalesLoading, setAfterSalesLoading] = useState(false);
  const [afterSalesError, setAfterSalesError] = useState<string | null>(null);
  const [paymentPolling, setPaymentPolling] = useState<PaymentPollingTask | null>(null);

  const [appliedCoupon, setAppliedCoupon] = useState<Coupon | null>(null);

  // 首屏从后端加载门户数据，接口不可用时保留演示数据，保证页面仍可预览。
  useEffect(() => {
    let active = true;
    Promise.allSettled([fetchPortalProducts(), fetchPortalCoupons(), fetchPortalBanners()])
      .then(([productResult, couponResult, bannerResult]) => {
        if (!active) return;
        if (productResult.status === 'fulfilled' && productResult.value.length > 0) setProducts(productResult.value);
        if (couponResult.status === 'fulfilled' && couponResult.value.length > 0) setCoupons(couponResult.value);
        if (bannerResult.status === 'fulfilled' && bannerResult.value.length > 0) setBanners(bannerResult.value);
      })
      .catch((error) => console.warn('门户后端暂不可用，继续使用演示数据', error));
    return () => {
      active = false;
    };
  }, []);

  // Claimed coupons in user's account
  const [claimedCouponCodes, setClaimedCouponCodes] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem('aurora_claimed_coupons');
      const parsed = saved ? JSON.parse(saved) : DEMO_MODE ? ['AURORA20'] : [];
      return Array.isArray(parsed) ? parsed : DEMO_MODE ? ['AURORA20'] : [];
    } catch {
      return DEMO_MODE ? ['AURORA20'] : [];
    }
  });

  // User Authentication State & Session
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(() => {
    try {
      const saved = localStorage.getItem('aurora_user_session');
      // 生产模式仅恢复仍持有访问令牌的会话，避免历史缓存绕过登录拦截。
      return DEMO_MODE ? (saved ? JSON.parse(saved) : PRESET_TEST_USERS[0].user)
        : (saved && hasPortalMemberSession() ? JSON.parse(saved) : null);
    } catch {
      return DEMO_MODE ? PRESET_TEST_USERS[0].user : null;
    }
  });
  const [memberAddresses, setMemberAddresses] = useState<import('./types/ecommerce').Address[]>([]);

  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [authModalMode, setAuthModalMode] = useState<AuthMode>('login-pwd');
  const [isUserProfileModalOpen, setIsUserProfileModalOpen] = useState(false);

  // 登录会员存在可映射的数字 ID 时，加载服务端购物车、收藏和订单。
  useEffect(() => {
    const memberId = resolveMemberId(currentUser);
    if (!memberId) return;
    let active = true;
    Promise.all([
      fetchPortalCart(memberId),
      fetchPortalFavorites(memberId),
      fetchPortalOrders(memberId),
      fetchPortalAddresses(memberId),
    ])
      .then(async ([remoteCart, remoteFavorites, remoteOrders, remoteAddresses]) => {
        if (!active) return;
        const productMap = new Map<number, Product>(
          products.map((product): [number, Product] => [Number(product.id.replace('prod-', '')), product])
        );
        const mappedCart = remoteCart
          .map((item) => {
            const product = productMap.get(item.productId);
            if (!product) return null;
            return {
              id: `server-${item.id}`,
              productId: product.id,
              skuId: item.skuId,
              product,
              selectedVariants: {},
              quantity: item.quantity,
              unitPrice: product.price,
              selected: item.selected === 1,
            } as CartItem;
          })
          .filter((item): item is CartItem => Boolean(item));
        if (mappedCart.length > 0) setCartItems(mappedCart);
        if (remoteFavorites.length > 0) setWishlist(remoteFavorites.map((item) => `prod-${item.productId}`));
        if (remoteAddresses.length > 0) {
          setMemberAddresses(remoteAddresses.map((address) => ({
            id: String(address.id),
            receiverName: address.receiverName,
            phone: address.receiverPhone,
            province: address.province,
            city: address.city,
            district: address.district,
            detail: address.detailAddress,
            tag: address.addressTag as '家' | '公司' | '学校' | undefined,
            isDefault: address.isDefault === 1,
          })));
        }
        // 物流轨迹优先走独立接口，详情接口作为兼容回退，同时补齐商品明细。
        const details = await Promise.all(remoteOrders.map(async (order) => {
          let remoteLogistics: PortalOrderLogisticsRecord[] | null = null;
          try {
            remoteLogistics = await fetchPortalOrderLogistics(order.id);
          } catch (error) {
            console.warn(`订单 ${order.orderNo} 物流轨迹加载失败`, error);
          }
          let detail = null as Awaited<ReturnType<typeof fetchPortalOrderDetail>> | null;
          try {
            detail = await fetchPortalOrderDetail(order.id);
          } catch (error) {
            console.warn(`订单 ${order.orderNo} 详情加载失败`, error);
          }
          const fallbackLogistics = detail?.logistics || [];
          const logistics = remoteLogistics && remoteLogistics.length > 0
            ? remoteLogistics
            : fallbackLogistics;
          return {
            detail,
            logistics,
            logisticsUnavailable: remoteLogistics === null && logistics.length === 0,
          };
        }));
        if (!active) return;
        setOrders(remoteOrders.map((order, index) => {
          const detailResult = details[index];
          const detail = detailResult.detail;
          const status = mapPortalOrderStatus(order.orderStatus);
          const detailItems = detail?.items || [];
          const detailLogistics = detailResult.logistics;
          return {
            id: String(order.id),
            orderNumber: order.orderNo,
            trackingNumber: order.trackingNo || detail?.order?.trackingNo || detailLogistics[0]?.trackingNo || '',
            carrier: order.logisticsCompany || detail?.order?.logisticsCompany || detailLogistics[0]?.logisticsCompany || undefined,
            createdAt: formatPortalDate(order.createdAt),
            status: status.status,
            statusLabel: status.label,
            items: detailItems.map((item) => mapPortalOrderItem(item, products)),
            subtotal: Number(order.subtotalAmount || 0),
            discount: Number(order.discountAmount || 0),
            shippingFee: Number(order.freightAmount || 0),
            totalPaid: Number(order.paidAmount || order.payableAmount || 0),
            shippingAddress: {
              id: `order-${order.id}`,
              receiverName: order.receiverName,
              phone: order.receiverPhone,
              province: order.receiverProvince || '',
              city: order.receiverCity || '',
              district: order.receiverDistrict || '',
              detail: order.receiverAddress,
              isDefault: false,
            },
            paymentMethod: order.paymentMethod || '在线支付',
            estimatedDelivery: detailLogistics.length > 0
              ? '物流持续更新中'
              : detailResult.logisticsUnavailable
              ? '物流轨迹暂不可用'
              : '以物流轨迹为准',
            trackingSteps: detailLogistics.length > 0
              ? mapPortalLogistics(detailLogistics)
              : detailResult.logisticsUnavailable
              ? [{
                  title: '物流轨迹加载失败',
                  time: '稍后重试',
                  completed: false,
                  description: '物流服务暂时不可用，请稍后刷新订单查看最新节点。',
                }]
              : [{
                  title: '暂无物流轨迹',
                  time: '待同步',
                  completed: false,
                  description: '物流服务尚未返回新的轨迹节点，请稍后刷新订单。',
                }],
          };
        }));
      })
      .catch((error) => console.warn('会员数据接口暂不可用，继续使用本地数据', error));
    return () => {
      active = false;
    };
  }, [currentUser, products]);

  // 支付单创建后轮询渠道状态，达到成功、失败或超时终态后立即清理定时器。
  useEffect(() => {
    const task = paymentPolling;
    if (!task) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const pollInterval = 2000;

    const finish = async (state: NonNullable<Order['paymentState']>, status: number, message: string) => {
      if (!active) return;
      if (state === 'expired') {
        // 超时主动关闭支付单，关闭失败不影响前端结束轮询和提示状态。
        await closePortalPayment(task.memberId, task.paymentNo).catch((error) => {
          console.warn('支付单超时关闭失败', error);
        });
        if (!active) return;
      }
      const updatedOrder: Order = {
        ...task.order,
        paymentNo: task.paymentNo,
        paymentStatus: status,
        paymentState: state,
        status: state === 'succeeded' ? 'paid' : task.order.status,
        statusLabel: state === 'succeeded' ? '已支付，等待发货' : state === 'failed' ? '支付失败' : '支付超时',
        trackingSteps: state === 'succeeded'
          ? task.order.trackingSteps.map((step, index) => index === 1
            ? { ...step, completed: true, time: '刚刚', description: '支付回调已确认，系统正在安排仓库拣货。' }
            : step)
          : task.order.trackingSteps,
      };
      setOrders((previous) => previous.map((order) => order.id === updatedOrder.id ? updatedOrder : order));
      setPaymentPolling(null);
      if (state === 'succeeded') {
        setCompletedOrder(updatedOrder);
        showToast(message, 'success');
      } else {
        showToast(message, 'error');
      }
    };

    const poll = async () => {
      if (!active) return;
      if (Date.now() >= task.expiresAt) {
        await finish('expired', 3, '支付超时，支付单已关闭，请重新发起支付');
        return;
      }
      try {
        const payment = await fetchPortalPayment(task.memberId, task.paymentNo);
        if (!active) return;
        const state = mapPaymentState(payment.status);
        if (state === 'succeeded') {
          await finish(state, payment.status, '🎉 支付成功，订单已进入配货流程！');
          return;
        }
        if (state === 'failed') {
          await finish(state, payment.status, '支付失败，请检查支付方式后重试');
          return;
        }
        if (state === 'expired') {
          await finish(state, payment.status, '支付已超时，支付单已关闭');
          return;
        }
      } catch (error) {
        // 查询暂时失败时继续重试，直到超时窗口结束，避免单次网络抖动误判支付失败。
        console.warn('支付状态查询失败，将继续重试', error);
      }
      if (active) timer = setTimeout(() => void poll(), pollInterval);
    };

    void poll();
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [paymentPolling]);

  // 登录会员切换后独立加载售后记录，避免售后接口异常影响订单列表展示。
  useEffect(() => {
    const memberId = resolveMemberId(currentUser);
    setAfterSales([]);
    setAfterSalesError(null);
    if (!memberId) {
      setAfterSalesLoading(false);
      return;
    }
    let active = true;
    setAfterSalesLoading(true);
    fetchPortalAfterSales(memberId)
      .then((records) => {
        if (active) setAfterSales(records || []);
      })
      .catch((error) => {
        if (active) {
          console.warn('售后记录加载失败', error);
          setAfterSalesError(error instanceof Error ? error.message : '售后记录加载失败，请稍后重试');
        }
      })
      .finally(() => {
        if (active) setAfterSalesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [currentUser]);

  // Modals & Drawers
  const [quickViewProduct, setQuickViewProduct] = useState<Product | null>(null);
  const [isCartOpen, setIsCartOpen] = useState(false);
  const [isCheckoutOpen, setIsCheckoutOpen] = useState(false);
  const [checkoutItems, setCheckoutItems] = useState<CartItem[]>([]);
  const [completedOrder, setCompletedOrder] = useState<Order | null>(null);
  const [isOrdersOpen, setIsOrdersOpen] = useState(false);
  const [isWishlistOpen, setIsWishlistOpen] = useState(false);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  // Derived claimed coupons objects
  const claimedCoupons = useMemo(() => {
    return coupons.filter((c) => claimedCouponCodes.includes(c.code));
  }, [claimedCouponCodes, coupons]);

  // Sync claimed coupons to localStorage and update user profile count
  useEffect(() => {
    try {
      localStorage.setItem('aurora_claimed_coupons', JSON.stringify(claimedCouponCodes));
    } catch (e) {
      console.error(e);
    }
    if (currentUser && currentUser.couponsCount !== claimedCouponCodes.length) {
      setCurrentUser((prev) => (prev ? { ...prev, couponsCount: claimedCouponCodes.length } : null));
    }
  }, [claimedCouponCodes]);

  // Automatic Coupon Association: Auto-match best eligible coupon for current active cart
  useEffect(() => {
    const selectedCartItems = cartItems.filter((i) => i.selected);
    const subtotal = selectedCartItems.reduce((acc, item) => acc + item.unitPrice * item.quantity, 0);

    if (subtotal === 0 || claimedCoupons.length === 0) {
      if (appliedCoupon && subtotal === 0) {
        setAppliedCoupon(null);
      }
      return;
    }

    // Find all claimed coupons that meet the min spend
    const eligibleCoupons = claimedCoupons
      .filter((c) => subtotal >= c.minSpend)
      .sort((a, b) => b.discountAmount - a.discountAmount);

    if (eligibleCoupons.length > 0) {
      const bestCoupon = eligibleCoupons[0];
      // If currently no coupon applied, or applied coupon is no longer eligible or offers less discount
      if (
        !appliedCoupon ||
        subtotal < appliedCoupon.minSpend ||
        !claimedCouponCodes.includes(appliedCoupon.code) ||
        (bestCoupon.discountAmount > appliedCoupon.discountAmount)
      ) {
        setAppliedCoupon(bestCoupon);
      }
    } else if (appliedCoupon && subtotal < appliedCoupon.minSpend) {
      // Subtotal dropped below threshold
      setAppliedCoupon(null);
    }
  }, [cartItems, claimedCoupons, claimedCouponCodes]);

  // Sync user session to localStorage
  useEffect(() => {
    try {
      if (currentUser) {
        localStorage.setItem('aurora_user_session', JSON.stringify(currentUser));
      } else {
        localStorage.removeItem('aurora_user_session');
      }
    } catch (e) {
      console.error(e);
    }
  }, [currentUser]);

  // 统一拦截 API 返回的会员会话失效事件，清理本地 JWT 并回到未登录状态。
  useEffect(() => {
    const handleSessionExpired = () => {
      setCurrentUser(null);
      setPaymentPolling(null);
      clearPortalMemberToken();
      showToast('登录已过期，请重新登录', 'error');
    };
    window.addEventListener('henfon:member-session-expired', handleSessionExpired);
    const handleStorage = (event: StorageEvent) => {
      if (event.key === 'henfon_shop_member_token' && !event.newValue) {
        handleSessionExpired();
      }
    };
    window.addEventListener('storage', handleStorage);
    return () => {
      window.removeEventListener('henfon:member-session-expired', handleSessionExpired);
      window.removeEventListener('storage', handleStorage);
    };
  }, []);

  // Auth Handlers
  const handleOpenAuth = (mode: AuthMode = 'login-pwd') => {
    setAuthModalMode(mode);
    setIsAuthModalOpen(true);
  };

  const handleLoginSuccess = (user: UserProfile, message: string) => {
    setCurrentUser(user);
    showToast(message, 'success');
  };

  const handleLogout = () => {
    // 服务端优先吊销当前令牌；即使接口不可用也清理本地会话，避免继续携带旧令牌。
    void logoutPortalMember()
      .catch((error) => console.warn('门户退出接口暂不可用，已清理本地会话', error))
      .finally(() => {
        setCurrentUser(null);
        clearPortalMemberToken();
        showToast('您已成功退出登录', 'info');
      });
  };

  const handleUpdateUser = (updatedUser: UserProfile) => {
    setCurrentUser(updatedUser);
    showToast('个人资料与偏好设置已更新', 'success');
  };

  // 打开商品详情时补充后端的卖点、参数和媒体数据。
  const openProduct = async (product: Product) => {
    setQuickViewProduct(product);
    try {
      const detail = await fetchPortalProductDetail(product.id);
      if (detail) setQuickViewProduct((current) => (current?.id === product.id ? { ...current, ...detail } : current));
    } catch (error) {
      console.warn('商品详情接口暂不可用，继续使用列表数据', error);
    }
  };

  // Sync to localStorage
  useEffect(() => {
    try {
      localStorage.setItem('aurora_cart', JSON.stringify(cartItems));
    } catch (e) {
      console.error(e);
    }
  }, [cartItems]);

  useEffect(() => {
    try {
      localStorage.setItem('aurora_wishlist', JSON.stringify(wishlist));
    } catch (e) {
      console.error(e);
    }
  }, [wishlist]);

  useEffect(() => {
    try {
      localStorage.setItem('aurora_orders', JSON.stringify(orders));
    } catch (e) {
      console.error(e);
    }
  }, [orders]);

  // Toast Helper
  const showToast = (message: string, type: 'success' | 'error' | 'info' = 'success') => {
    const id = `toast-${Date.now()}-${Math.random()}`;
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3500);
  };

  const handleDismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  // Cart Calculations
  const cartCount = cartItems.reduce((sum, it) => sum + it.quantity, 0);
  const cartTotal = cartItems
    .filter((it) => it.selected)
    .reduce((sum, it) => sum + it.unitPrice * it.quantity, 0);

  // Cart Handlers
  const handleAddToCart = (
    product: Product,
    selectedVariants: Record<string, string> = {},
    quantity = 1
  ) => {
    // If no variants passed, pick defaults
    const finalVariants: Record<string, string> = { ...selectedVariants };
    if (product.variants && Object.keys(finalVariants).length === 0) {
      product.variants.forEach((v) => {
        finalVariants[v.name] = v.options[0]?.label || '';
      });
    }

    const selectedSku = resolveSelectedSku(product, finalVariants);
    // Calculate unit price modifier
    let finalUnitPrice = selectedSku?.price ?? product.price;
    if (!selectedSku && product.variants) {
      product.variants.forEach((variant) => {
        const selectedOptionLabel = finalVariants[variant.name];
        const found = variant.options.find((o) => o.label === selectedOptionLabel);
        if (found?.priceModifier) {
          finalUnitPrice += found.priceModifier;
        }
      });
    }
    const availableStock = selectedSku?.stock ?? (product.skus?.length ? 0 : product.stock);

    const cartKey = `${product.id}-${JSON.stringify(finalVariants)}`;

    setCartItems((prev) => {
      const existing = prev.find((item) => item.id === cartKey);
      if (existing) {
        return prev.map((item) =>
          item.id === cartKey
            ? { ...item, quantity: Math.min(availableStock, item.quantity + quantity) }
            : item
        );
      } else {
        return [
          ...prev,
          {
            id: cartKey,
            productId: product.id,
            skuId: selectedSku?.id,
            product,
            selectedVariants: finalVariants,
            quantity,
            unitPrice: finalUnitPrice,
            selected: true,
          },
        ];
      }
    });

    const memberId = resolveMemberId(currentUser);
    const productId = Number(product.id.replace('prod-', ''));
    if (memberId && Number.isFinite(productId)) {
      addPortalCartItem(memberId, productId, quantity, selectedSku?.id).catch((error) =>
        console.warn('购物车同步失败，已保留本地购物车', error)
      );
    }

    showToast(`已将《${product.title}》加入购物车`);
  };

  const handleBatchAddToCart = (
    items: {
      product: Product;
      selectedVariants?: Record<string, string>;
      quantity?: number;
    }[]
  ) => {
    if (items.length === 0) return;

    setCartItems((prev) => {
      let updated = [...prev];

      items.forEach(({ product, selectedVariants = {}, quantity = 1 }) => {
        const finalVariants: Record<string, string> = { ...selectedVariants };
        if (product.variants && Object.keys(finalVariants).length === 0) {
          product.variants.forEach((v) => {
            finalVariants[v.name] = v.options[0]?.label || '';
          });
        }

        const selectedSku = resolveSelectedSku(product, finalVariants);
        let finalUnitPrice = selectedSku?.price ?? product.price;
        if (!selectedSku && product.variants) {
          product.variants.forEach((variant) => {
            const selectedOptionLabel = finalVariants[variant.name];
            const found = variant.options.find((o) => o.label === selectedOptionLabel);
            if (found?.priceModifier) {
              finalUnitPrice += found.priceModifier;
            }
          });
        }

        const cartKey = `${product.id}-${JSON.stringify(finalVariants)}`;
        const existingIndex = updated.findIndex((it) => it.id === cartKey);

        if (existingIndex >= 0) {
          updated[existingIndex] = {
            ...updated[existingIndex],
            quantity: Math.min(selectedSku?.stock ?? (product.skus?.length ? 0 : product.stock), updated[existingIndex].quantity + quantity),
            selected: true,
          };
        } else {
          updated.push({
            id: cartKey,
            productId: product.id,
            skuId: selectedSku?.id,
            product,
            selectedVariants: finalVariants,
            quantity,
            selected: true,
            unitPrice: finalUnitPrice,
          });
        }
      });

      return updated;
    });

    if (items.length === 1) {
      showToast(`已将《${items[0].product.title}》（默认规格）加入购物车`);
    } else {
      showToast(`已将 ${items.length} 款对比商品（含默认规格）批量加入购物车！`, 'success');
    }
  };

  const handleDirectBuy = (
    product: Product,
    selectedVariants: Record<string, string>,
    quantity: number
  ) => {
    const selectedSku = resolveSelectedSku(product, selectedVariants);
    let finalUnitPrice = selectedSku?.price ?? product.price;
    if (!selectedSku && product.variants) {
      product.variants.forEach((variant) => {
        const selectedOptionLabel = selectedVariants[variant.name];
        const found = variant.options.find((o) => o.label === selectedOptionLabel);
        if (found?.priceModifier) {
          finalUnitPrice += found.priceModifier;
        }
      });
    }

    const tempItem: CartItem = {
      id: `instant-${product.id}-${Date.now()}`,
      productId: product.id,
      skuId: selectedSku?.id,
      product,
      selectedVariants,
      quantity,
      unitPrice: finalUnitPrice,
      selected: true,
    };

    setCheckoutItems([tempItem]);
    setQuickViewProduct(null);
    setIsCheckoutOpen(true);
  };

  const handleUpdateQuantity = (cartItemId: string, newQuantity: number) => {
    if (newQuantity <= 0) {
      handleRemoveCartItem(cartItemId);
      return;
    }
    setCartItems((prev) =>
      prev.map((it) => (it.id === cartItemId ? { ...it, quantity: newQuantity } : it))
    );
    const serverId = cartItemId.match(/^server-(\d+)$/);
    if (serverId) updatePortalCartItem(Number(serverId[1]), newQuantity).catch(console.warn);
  };

  const handleToggleSelectItem = (cartItemId: string) => {
    setCartItems((prev) =>
      prev.map((it) => (it.id === cartItemId ? { ...it, selected: !it.selected } : it))
    );
    const serverId = cartItemId.match(/^server-(\d+)$/);
    const current = cartItems.find((item) => item.id === cartItemId);
    if (serverId && current) updatePortalCartItem(Number(serverId[1]), undefined, !current.selected).catch(console.warn);
  };

  const handleToggleSelectAll = (select: boolean) => {
    setCartItems((prev) => prev.map((it) => ({ ...it, selected: select })));
  };

  const handleRemoveCartItem = (cartItemId: string) => {
    setCartItems((prev) => prev.filter((it) => it.id !== cartItemId));
    const serverId = cartItemId.match(/^server-(\d+)$/);
    if (serverId) deletePortalCartItem(Number(serverId[1])).catch(console.warn);
    showToast('已从购物车中移除商品', 'info');
  };

  const handleClearCart = () => {
    setCartItems([]);
    showToast('购物车已清空', 'info');
  };

  // Wishlist Handlers
  const handleToggleWishlist = (productId: string) => {
    if (wishlist.includes(productId)) {
      setWishlist((prev) => prev.filter((id) => id !== productId));
      showToast('已从收藏夹移除', 'info');
    } else {
      setWishlist((prev) => [...prev, productId]);
      showToast('已加入心愿收藏夹！');
    }
    const memberId = resolveMemberId(currentUser);
    const numericProductId = Number(productId.replace('prod-', ''));
    if (memberId && Number.isFinite(numericProductId)) {
      togglePortalFavorite(memberId, numericProductId).catch((error) =>
        console.warn('收藏同步失败，已保留本地收藏状态', error)
      );
    }
  };

  // Compare Handlers & History Persistence
  const saveCompareHistory = (productIds: string[]) => {
    if (!productIds || productIds.length === 0) return;

    setLastComparedProductIds(productIds);
    try {
      localStorage.setItem('aurora_last_compare', JSON.stringify(productIds));
    } catch (e) {
      console.error(e);
    }

    setCompareHistory((prev) => {
      const sortedNew = [...productIds].sort().join(',');
      const isSameAsLatest =
        prev.length > 0 && [...prev[0].productIds].sort().join(',') === sortedNew;

      let updated: CompareHistoryItem[];
      if (isSameAsLatest) {
        updated = [{ ...prev[0], timestamp: Date.now() }, ...prev.slice(1)];
      } else {
        const newItem: CompareHistoryItem = {
          id: `comp-hist-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
          timestamp: Date.now(),
          productIds,
        };
        updated = [
          newItem,
          ...prev.filter((item) => [...item.productIds].sort().join(',') !== sortedNew),
        ].slice(0, 10);
      }

      try {
        localStorage.setItem('aurora_compare_history', JSON.stringify(updated));
      } catch (e) {
        console.error(e);
      }
      return updated;
    });
  };

  const handleToggleCompare = (product: Product) => {
    if (compareProductIds.includes(product.id)) {
      const nextList = compareProductIds.filter((id) => id !== product.id);
      if (nextList.length === 0 && compareProductIds.length > 0) {
        saveCompareHistory(compareProductIds);
      }
      setCompareProductIds(nextList);
      showToast(`已从对比栏移除《${product.title}》`, 'info');
    } else {
      if (compareProductIds.length >= 3) {
        showToast('最多同时支持对比 3 款商品，请先移除其中一款后再添加', 'error');
        return;
      }
      const nextList = [...compareProductIds, product.id];
      setCompareProductIds(nextList);
      setIsCompareBarVisible(true);
      if (nextList.length >= 2) {
        saveCompareHistory(nextList);
      }
      showToast(`已将《${product.title}》加入对比栏 (${nextList.length}/3)`);
    }
  };

  const handleClearCompare = () => {
    if (compareProductIds.length > 0) {
      saveCompareHistory(compareProductIds);
    }
    setCompareProductIds([]);
    showToast('已清空商品对比栏', 'info');
  };

  const handleCloseCompareBar = () => {
    if (compareProductIds.length > 0) {
      saveCompareHistory(compareProductIds);
    }
    setIsCompareBarVisible(false);
  };

  const handleRemoveFromCompare = (productId: string) => {
    if (compareProductIds.length > 0) {
      saveCompareHistory(compareProductIds);
    }
    setCompareProductIds((prev) => prev.filter((id) => id !== productId));
    showToast('已从对比栏移除该商品', 'info');
  };

  const handleRestoreLastCompare = () => {
    if (lastComparedProductIds.length === 0) {
      showToast('暂无历史对比记录', 'info');
      return;
    }
    setCompareProductIds(lastComparedProductIds);
    setIsCompareBarVisible(true);
    setIsCompareMode(true);
    showToast(`已恢复上次对比组合（${lastComparedProductIds.length} 款商品）`, 'success');
  };

  const handleRestoreFromHistory = (productIds: string[]) => {
    if (!productIds || productIds.length === 0) return;
    setCompareProductIds(productIds);
    setIsCompareBarVisible(true);
    setIsCompareMode(true);
    showToast(`已恢复对比组合（${productIds.length} 款商品）`, 'success');
  };

  const handleDeleteHistoryItem = (historyId: string) => {
    setCompareHistory((prev) => {
      const updated = prev.filter((item) => item.id !== historyId);
      try {
        localStorage.setItem('aurora_compare_history', JSON.stringify(updated));
      } catch (e) {
        console.error(e);
      }
      return updated;
    });
    showToast('已删除该条历史记录', 'info');
  };

  const handleClearCompareHistory = () => {
    setCompareHistory([]);
    setLastComparedProductIds([]);
    try {
      localStorage.removeItem('aurora_compare_history');
      localStorage.removeItem('aurora_last_compare');
    } catch (e) {
      console.error(e);
    }
    showToast('已清空所有对比历史记录', 'info');
  };

  // Coupon Handlers & Claim Operations
  const handleClaimCoupon = async (coupon: Coupon) => {
    if (claimedCouponCodes.includes(coupon.code)) {
      showToast(`您已经领取过《${coupon.title}》啦`, 'info');
      return;
    }

    // 有真实优惠券 ID 时优先写入服务端，接口失败则不伪造本地领取成功状态。
    const memberId = resolveMemberId(currentUser);
    if (memberId && coupon.id) {
      try {
        await claimPortalCoupon(coupon.id);
      } catch (error) {
        console.warn('优惠券领取同步失败', error);
        showToast(error instanceof Error ? error.message : '优惠券领取失败，请稍后重试', 'error');
        return;
      }
    }

    const nextCodes = [...claimedCouponCodes, coupon.code];
    setClaimedCouponCodes(nextCodes);

    // Calculate current cart subtotal to see if we can auto-associate immediately
    const selectedCartItems = cartItems.filter((i) => i.selected);
    const subtotal = selectedCartItems.reduce((acc, it) => acc + it.unitPrice * it.quantity, 0);

    if (
      subtotal >= coupon.minSpend &&
      (!appliedCoupon || coupon.discountAmount > appliedCoupon.discountAmount)
    ) {
      setAppliedCoupon(coupon);
      showToast(
        `🎉 成功领取《${coupon.title}》，已在结算时自动为您关联抵扣 ¥${coupon.discountAmount}！`,
        'success'
      );
    } else {
      showToast(`🎉 成功领取《${coupon.title}》！满 ¥${coupon.minSpend} 即可自动抵扣`, 'success');
    }
  };

  const handleClaimAllCoupons = () => {
    const allCodes = coupons.map((c) => c.code);
    const newCodes = Array.from(new Set([...claimedCouponCodes, ...allCodes]));
    const newlyAddedCount = newCodes.length - claimedCouponCodes.length;

    if (newlyAddedCount === 0) {
      showToast('所有可用优惠券均已在您的账户中', 'info');
      return;
    }

    setClaimedCouponCodes(newCodes);

    // Auto-apply highest eligible coupon if cart has items
    const selectedCartItems = cartItems.filter((i) => i.selected);
    const subtotal = selectedCartItems.reduce((acc, it) => acc + it.unitPrice * it.quantity, 0);
    const eligible = coupons.filter((c) => subtotal >= c.minSpend).sort(
      (a, b) => b.discountAmount - a.discountAmount
    );

    if (eligible.length > 0 && (!appliedCoupon || eligible[0].discountAmount > appliedCoupon.discountAmount)) {
      setAppliedCoupon(eligible[0]);
      showToast(
        `🎁 一键领取成功！已收入 ${newlyAddedCount} 张神券，并自动关联最优减免 -¥${eligible[0].discountAmount}！`,
        'success'
      );
    } else {
      showToast(`🎁 一键领取成功！已成功将 ${newlyAddedCount} 张神券收入账户。`, 'success');
    }
  };

  const handleApplyCoupon = (code: string): boolean => {
    const coupon = coupons.find(
      (c) => c.code.toUpperCase() === code.trim().toUpperCase()
    );
    if (coupon) {
      if (!claimedCouponCodes.includes(coupon.code)) {
        setClaimedCouponCodes((prev) => [...prev, coupon.code]);
      }
      setAppliedCoupon(coupon);
      showToast(`已成功使用优惠券：${coupon.title} (立减 ¥${coupon.discountAmount})`, 'success');
      return true;
    }
    return false;
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon(null);
    showToast('已取消使用优惠券', 'info');
  };

  const handleOpenCouponCenter = () => {
    const el = document.getElementById('coupon-center-section');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  // Checkout Handlers
  const handleOpenCheckoutFromCart = () => {
    const selected = cartItems.filter((i) => i.selected);
    if (selected.length === 0) {
      showToast('请先勾选需要结算的商品', 'error');
      return;
    }
    setCheckoutItems(selected);
    setIsCartOpen(false);
    setIsCheckoutOpen(true);
  };

  const handlePlaceOrderSuccess = (newOrder: Order, persistence?: CheckoutPersistenceResult) => {
    if (persistence?.paymentNo) {
      const memberId = resolveMemberId(currentUser);
      if (memberId) {
        const paymentStatus = persistence.paymentStatus;
        const paymentState = mapPaymentState(paymentStatus);
        const persistedOrder: Order = {
          ...newOrder,
          paymentNo: persistence.paymentNo,
          paymentStatus,
          paymentState,
          status: paymentState === 'succeeded' ? 'paid' : newOrder.status,
          statusLabel: paymentState === 'succeeded' ? '已支付，等待发货' : '待支付',
        };
        setOrders((prev) => [persistedOrder, ...prev.filter((order) => order.id !== persistedOrder.id)]);
        const purchasedProductIds = new Set(persistedOrder.items.map((item) => item.productId));
        setCartItems((prev) => prev.filter((item) => !purchasedProductIds.has(item.productId) || !item.selected));
        setIsCheckoutOpen(false);
        setPaymentPolling({
          memberId,
          paymentNo: persistence.paymentNo,
          order: persistedOrder,
          expiresAt: Date.now() + 2 * 60 * 1000,
        });
        showToast('支付单已生成，正在确认支付状态…', 'info');
        return;
      }
    }
    setOrders((prev) => [newOrder, ...prev]);

    // Remove purchased items from cart if they were from cart
    const purchasedProductIds = new Set(newOrder.items.map((i) => i.productId));
    setCartItems((prev) => prev.filter((it) => !purchasedProductIds.has(it.productId) || !it.selected));

    setIsCheckoutOpen(false);
    if (newOrder.status === 'paid') {
      setCompletedOrder(newOrder);
      showToast('🎉 订单支付成功，已进入配货流程！');
    } else {
      // 支付单创建成功但尚未收到渠道回调时，订单保持待付款，避免误导为已支付。
      showToast('订单已创建，支付单已生成，请完成支付后等待回调确认', 'info');
    }
  };

  const handlePersistOrder = async (order: Order): Promise<CheckoutPersistenceResult> => {
    const memberId = resolveMemberId(currentUser);
    if (!memberId) {
      return {
        paymentCreated: false,
        message: '当前登录账号无法映射服务端会员，订单已保留在本地，请登录后重试支付',
      };
    }
    const province = order.shippingAddress.province;
    const city = order.shippingAddress.city;
    const district = order.shippingAddress.district;
    let serverOrderId: number | undefined;
    let serverOrderNo: string | undefined;
    try {
      const serverOrder = await createPortalOrder({
        memberId,
        // 使用本地订单 ID 作为幂等键，网络重试时仍能定位同一笔订单。
        idempotencyKey: order.id,
        items: order.items.map((item) => {
          const product = products.find((candidate) => candidate.id === item.productId);
          const sku = product ? resolveSelectedSku(product, Object.fromEntries(
            item.variantsSummary.split(' / ').map((entry) => entry.split(':')).filter((entry) => entry.length === 2)
          )) : undefined;
          return {
            productId: Number(item.productId.replace('prod-', '')) || undefined,
            skuId: item.skuId || sku?.id,
            skuCode: sku?.skuCode,
            productName: item.title,
            imageUrl: item.image,
            unitPrice: item.price,
            quantity: item.quantity,
            skuName: item.variantsSummary,
          };
        }),
        receiverName: order.shippingAddress.receiverName,
        receiverPhone: order.shippingAddress.phone,
        receiverProvince: province,
        receiverCity: city,
        receiverDistrict: district,
        receiverAddress: order.shippingAddress.detail,
        paymentMethod: order.paymentMethod,
        subtotalAmount: order.subtotal,
        // 优惠金额由后端在优惠券核销时重新计算，创建订单阶段只提交未优惠金额。
        discountAmount: 0,
        freightAmount: order.shippingFee,
        payableAmount: order.subtotal + order.shippingFee,
      });
      serverOrderId = serverOrder.id;
      serverOrderNo = serverOrder.orderNo;

      // 支付渠道暂由基础支付单承接，后续接入微信/支付宝 SDK 时复用该支付单号。
      const paymentChannels: Record<string, string> = {
        微信支付: 'WECHAT',
        支付宝: 'ALIPAY',
        云闪付: 'UNIONPAY',
        'Apple Pay': 'APPLEPAY',
      };
      const channel = paymentChannels[order.paymentMethod] || 'WECHAT';
      // 订单创建成功后记录优惠券核销，支付单必须建立在服务端确认优惠金额之后。
      if (appliedCoupon?.id) {
        // 核销失败时阻止创建支付单，避免以未核销优惠金额发起支付。
        await redeemPortalCoupon(appliedCoupon.id, serverOrder.id);
      }
      const payment = await createPortalPayment(memberId, serverOrder.id, channel);
      return {
        serverOrderId: serverOrder.id,
        serverOrderNo: serverOrder.orderNo,
        paymentNo: payment.paymentNo,
        paymentStatus: payment.status,
        paymentCreated: true,
      };
    } catch (error) {
      // 后端暂不可用时仍完成本地演示下单，同时明确提示支付单未创建成功。
      console.warn('订单或支付单同步失败，已保留本地订单', error);
      return {
        serverOrderId,
        serverOrderNo,
        paymentCreated: false,
        message: serverOrderId
          ? '订单已创建，但支付单创建失败，请稍后重试支付'
          : '订单服务暂不可用，订单已保留在本地，请稍后重试',
      };
    }
  };

  const handleCancelOrder = async (order: Order) => {
    const memberId = resolveMemberId(currentUser);
    const orderId = Number(order.id);
    if (!memberId || !Number.isFinite(orderId)) {
      showToast('当前订单尚未同步到服务端，暂不能取消', 'error');
      return;
    }
    try {
      await cancelPortalOrder(memberId, orderId, '会员主动取消');
      // 取消订单后回滚已核销优惠券，接口本身按订单幂等处理。
      await rollbackPortalCoupon(orderId).catch((error) => console.warn('优惠券回滚失败', error));
      setOrders((previous) => previous.map((item) => item.id === order.id
        ? { ...item, status: 'cancelled', statusLabel: '已取消' }
        : item));
      showToast('订单已取消，预占库存将自动释放', 'success');
    } catch (error) {
      console.error('取消订单失败', error);
      showToast(error instanceof Error ? error.message : '取消订单失败，请稍后重试', 'error');
    }
  };

  const handleConfirmOrder = async (order: Order) => {
    const memberId = resolveMemberId(currentUser);
    const orderId = Number(order.id);
    if (!memberId || !Number.isFinite(orderId)) {
      showToast('当前订单尚未同步到服务端，暂不能确认收货', 'error');
      return;
    }
    try {
      await confirmPortalOrder(memberId, orderId);
      setOrders((previous) => previous.map((item) => item.id === order.id
        ? { ...item, status: 'delivered', statusLabel: '已完成' }
        : item));
      showToast('已确认收货，感谢您的支持', 'success');
    } catch (error) {
      console.error('确认收货失败', error);
      showToast(error instanceof Error ? error.message : '确认收货失败，请稍后重试', 'error');
    }
  };

  const handleApplyAfterSale = async (order: Order) => {
    const memberId = resolveMemberId(currentUser);
    const orderId = Number(order.id);
    if (!memberId || !Number.isFinite(orderId)) {
      showToast('当前订单尚未同步到服务端，暂不能申请售后', 'error');
      return;
    }
    const typeInput = window.prompt('请选择售后类型：1 仅退款，2 退货退款，3 换货', '1');
    if (typeInput === null) return;
    const afterSaleType = Number(typeInput);
    if (![1, 2, 3].includes(afterSaleType)) {
      showToast('售后类型不合法，请重新申请', 'error');
      return;
    }
    const defaultAmount = afterSaleType === 3 ? '0' : String(order.totalPaid);
    const amountInput = window.prompt('请输入申请退款金额（元，换货填写 0）', defaultAmount);
    if (amountInput === null) return;
    const refundAmount = Number(amountInput);
    if (!Number.isFinite(refundAmount) || refundAmount < 0 || refundAmount > order.totalPaid) {
      showToast('退款金额不合法，不能超过订单实付金额', 'error');
      return;
    }
    const reason = window.prompt('请填写售后原因', '商品存在质量问题');
    if (!reason?.trim()) return;
    try {
      const record = await createPortalAfterSale(memberId, orderId, {
        afterSaleType: afterSaleType as 1 | 2 | 3,
        refundAmount,
        reason: reason.trim(),
      });
      setAfterSales((previous) => [record, ...previous.filter((item) => item.id !== record.id)]);
      showToast(`售后申请已提交（${record.afterSaleNo}），等待商家审核`, 'success');
    } catch (error) {
      console.error('申请售后失败', error);
      showToast(error instanceof Error ? error.message : '申请售后失败，请稍后重试', 'error');
    }
  };

  const handleApplyInvoice = async (order: Order) => {
    const orderId = Number(order.id);
    if (!resolveMemberId(currentUser) || !Number.isFinite(orderId)) {
      showToast('当前订单尚未同步到服务端，暂不能申请发票', 'error');
      return;
    }
    const existing = await fetchPortalOrderInvoice(orderId).catch(() => null);
    if (existing) {
      const statusLabels: Record<number, string> = { 0: '待开票', 1: '开票中', 2: '已开票', 3: '开票失败', 4: '已取消' };
      showToast(`该订单已有发票申请（${existing.invoiceNo}），状态：${statusLabels[existing.status] || '处理中'}`, 'info');
      return;
    }
    const invoiceTypeInput = window.prompt('请选择发票类型：1 普通发票，2 增值税专用发票', '1');
    const invoiceType = Number(invoiceTypeInput);
    if (invoiceType !== 1 && invoiceType !== 2) {
      showToast('发票类型不合法，请重新申请', 'error');
      return;
    }
    const title = window.prompt('请输入发票抬头', currentUser?.nickname || '个人');
    if (!title?.trim()) {
      showToast('发票抬头不能为空', 'error');
      return;
    }
    const taxNo = invoiceType === 2 ? window.prompt('请输入纳税人识别号（专票必填）', '') || '' : '';
    const email = window.prompt('请输入接收发票的邮箱（可选）', currentUser?.email || '') || '';
    try {
      const invoice = await applyPortalOrderInvoice(orderId, {
        invoiceType: invoiceType as 1 | 2,
        title: title.trim(),
        taxNo: taxNo.trim() || undefined,
        email: email.trim() || undefined,
      });
      showToast(`发票申请已提交（${invoice.invoiceNo}），等待商家开具`, 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '发票申请失败，请稍后重试', 'error');
    }
  };

  const handleCancelAfterSale = async (afterSale: PortalAfterSaleRecord) => {
    const memberId = resolveMemberId(currentUser);
    if (!memberId) {
      showToast('当前登录账号无法识别，暂不能取消售后', 'error');
      return;
    }
    if (!window.confirm(`确定取消售后申请“${afterSale.afterSaleNo}”吗？`)) return;
    try {
      await cancelPortalAfterSale(memberId, afterSale.id);
      setAfterSales((previous) => previous.map((item) => item.id === afterSale.id
        ? { ...item, status: 50 }
        : item));
      showToast('售后申请已取消', 'success');
    } catch (error) {
      console.error('取消售后失败', error);
      showToast(error instanceof Error ? error.message : '取消售后失败，请稍后重试', 'error');
    }
  };

  const handlePersistAddress = async (address: import('./types/ecommerce').Address) => {
    const memberId = resolveMemberId(currentUser);
    if (!memberId) return;
    const savedId = await savePortalAddress({
      id: /^\d+$/.test(address.id) ? Number(address.id) : undefined,
      memberId,
      receiverName: address.receiverName,
      receiverPhone: address.phone,
      province: address.province,
      city: address.city,
      district: address.district,
      detailAddress: address.detail,
      addressTag: address.tag,
      isDefault: address.isDefault ? 1 : 0,
    });
    const persistedAddress = { ...address, id: String(savedId) };
    setMemberAddresses((previous) => [
      ...(persistedAddress.isDefault ? previous.map((item) => ({ ...item, isDefault: false })) : previous),
      persistedAddress,
    ].filter((item, index, all) => all.findIndex((candidate) => candidate.id === item.id) === index));
  };

  const handleUpdateAddress = async (address: import('./types/ecommerce').Address) => {
    const memberId = resolveMemberId(currentUser);
    const addressId = Number(address.id);
    if (!memberId || !Number.isFinite(addressId)) return;
    await updatePortalAddress({
      id: addressId,
      memberId,
      receiverName: address.receiverName,
      receiverPhone: address.phone,
      province: address.province,
      city: address.city,
      district: address.district,
      detailAddress: address.detail,
      addressTag: address.tag,
      isDefault: address.isDefault ? 1 : 0,
    });
    setMemberAddresses((previous) => previous.map((item) => {
      if (address.isDefault) return item.id === address.id ? address : { ...item, isDefault: false };
      return item.id === address.id ? address : item;
    }));
  };

  const handleDeleteAddress = async (addressId: string) => {
    const memberId = resolveMemberId(currentUser);
    const numericAddressId = Number(addressId);
    if (!memberId || !Number.isFinite(numericAddressId)) return;
    try {
      await deletePortalAddress(memberId, numericAddressId);
      setMemberAddresses((previous) => previous.filter((item) => item.id !== addressId));
      showToast('收货地址已删除', 'success');
    } catch (error) {
      // 接口失败时保留原地址，避免本地状态与服务端不一致。
      showToast(error instanceof Error ? error.message : '删除地址失败，请稍后重试', 'error');
    }
  };

  const handleSetDefaultAddress = async (addressId: string) => {
    const memberId = resolveMemberId(currentUser);
    const numericAddressId = Number(addressId);
    if (!memberId || !Number.isFinite(numericAddressId)) return;
    try {
      await setDefaultPortalAddress(memberId, numericAddressId);
      setMemberAddresses((previous) => previous.map((item) => ({ ...item, isDefault: item.id === addressId })));
      showToast('默认地址已更新', 'success');
    } catch (error) {
      // 服务端拒绝时不更新本地默认标记，等待用户重试。
      showToast(error instanceof Error ? error.message : '设置默认地址失败，请稍后重试', 'error');
    }
  };

  // Filtered & Sorted Products
  const filteredProducts = useMemo(() => {
    return products
      .filter((prod) => {
        // Category filter
        if (selectedCategory !== 'all' && prod.category !== selectedCategory) {
          return false;
        }
        // Search filter
        if (searchQuery.trim()) {
          const q = searchQuery.toLowerCase();
          const matchTitle = prod.title.toLowerCase().includes(q);
          const matchSub = prod.subtitle.toLowerCase().includes(q);
          const matchBrand = prod.brand.toLowerCase().includes(q);
          const matchCat = prod.categoryLabel.toLowerCase().includes(q);
          if (!matchTitle && !matchSub && !matchBrand && !matchCat) return false;
        }
        // Stock filter
        if (onlyInStock && prod.stock <= 0) return false;
        // Discount filter
        if (onlyDiscount && prod.price >= prod.originalPrice) return false;
        // Price filter
        if (prod.price < priceRange[0] || prod.price > priceRange[1]) return false;

        return true;
      })
      .sort((a, b) => {
        switch (sortBy) {
          case 'sales':
            return b.salesCount - a.salesCount;
          case 'price-asc':
            return a.price - b.price;
          case 'price-desc':
            return b.price - a.price;
          case 'rating':
            return b.rating - a.rating;
          case 'newest':
            return b.id.localeCompare(a.id);
          case 'featured':
          default:
            return 0;
        }
      });
  }, [products, selectedCategory, searchQuery, onlyInStock, onlyDiscount, priceRange, sortBy]);

  const wishlistedProductsList = products.filter((p) => wishlist.includes(p.id));
  const comparedProductsList = products.filter((p) => compareProductIds.includes(p.id));

  const lastComparedProductsList = useMemo(() => {
    return products.filter((p) => lastComparedProductIds.includes(p.id));
  }, [products, lastComparedProductIds]);

  const compareHistoryWithProducts = useMemo(() => {
    return compareHistory
      .map((item) => ({
        id: item.id,
        timestamp: item.timestamp,
        products: products.filter((p) => item.productIds.includes(p.id)),
      }))
      .filter((item) => item.products.length > 0);
  }, [products, compareHistory]);

  return (
    <div className="min-h-screen bg-zinc-50/70 text-zinc-900 flex flex-col font-sans selection:bg-zinc-900 selection:text-white">
      {/* Toast Notification Layer */}
      <ToastContainer toasts={toasts} onDismiss={handleDismissToast} />

      {/* Main Navbar Header */}
      <Navbar
        cartCount={cartCount}
        cartTotal={cartTotal}
        wishlistCount={wishlist.length}
        ordersCount={orders.length}
        searchQuery={searchQuery}
        selectedCategory={selectedCategory}
        currentUser={currentUser}
        claimedCouponsCount={claimedCoupons.length}
        products={products}
        onSearchChange={setSearchQuery}
        onCategorySelect={(cat) => setSelectedCategory(cat)}
        onSelectProduct={openProduct}
        onOpenCart={() => setIsCartOpen(true)}
        onOpenWishlist={() => setIsWishlistOpen(true)}
        onOpenOrders={() => setIsOrdersOpen(true)}
        onOpenAuth={handleOpenAuth}
        onOpenUserProfile={() => setIsUserProfileModalOpen(true)}
        onOpenCouponCenter={handleOpenCouponCenter}
        onLogout={handleLogout}
      />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6 md:py-8">
        {/* Promotional Hero Carousel & Benefits (show only when no active text search query) */}
        {!searchQuery && selectedCategory === 'all' && (
          <HeroBanner
            banners={banners}
            onExploreCategory={(cat) => setSelectedCategory(cat)}
            onSelectProduct={(productId) => {
              const normalizedId = /^\d+$/.test(productId) ? `prod-${productId}` : productId;
              const p = products.find((it) => it.id === normalizedId) || PRODUCTS.find((it) => it.id === normalizedId);
              if (p) openProduct(p);
            }}
          />
        )}

        {/* Homepage Coupon Claiming Center (领券中心) */}
        <div id="coupon-center-section" className="mb-8 scroll-mt-24">
          <CouponCenter
            coupons={coupons}
            claimedCouponCodes={claimedCouponCodes}
            appliedCoupon={appliedCoupon}
            onClaimCoupon={handleClaimCoupon}
            onClaimAllCoupons={handleClaimAllCoupons}
            onUseCoupon={(c) => {
              handleApplyCoupon(c.code);
              setIsCartOpen(true);
            }}
          />
        </div>

        {/* Filter & Sort Controls Toolbar */}
        <section className="mb-6 bg-white rounded-2xl border border-zinc-200/90 p-4 sm:p-5 shadow-xs">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            {/* Left: Results Count & Active Filters */}
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-2">
                <span className="text-sm font-bold text-zinc-900">
                  {selectedCategory === 'all' ? '全部优选商品' : `品类筛选`}
                </span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-zinc-100 text-zinc-600 font-semibold">
                  共 {filteredProducts.length} 款
                </span>
              </div>

              {searchQuery && (
                <div className="flex items-center gap-1 text-xs px-2.5 py-1 rounded-lg bg-zinc-100 text-zinc-800">
                  <span>搜索: "{searchQuery}"</span>
                  <button onClick={() => setSearchQuery('')} className="ml-1 text-zinc-400 hover:text-zinc-900">
                    ✕
                  </button>
                </div>
              )}
            </div>

            {/* Right: Quick toggles & Sort selector & View Mode */}
            <div className="flex items-center gap-2.5 sm:gap-3 flex-wrap">
              {/* Compare Mode Button */}
              <div className="flex items-center gap-1.5">
                <button
                  onClick={() => {
                    const nextMode = !isCompareMode;
                    setIsCompareMode(nextMode);
                    if (nextMode && compareProductIds.length > 0) {
                      setIsCompareBarVisible(true);
                    }
                    showToast(
                      nextMode
                        ? '已开启对比模式，可在商品卡片上勾选最多 3 款商品进行对比'
                        : '已退出对比模式',
                      'info'
                    );
                  }}
                  className={`px-3 py-1.5 rounded-xl text-xs font-medium border transition flex items-center gap-1.5 ${
                    isCompareMode
                      ? 'bg-amber-400 border-amber-400 text-zinc-950 font-bold shadow-xs'
                      : 'bg-white text-zinc-700 border-zinc-200 hover:bg-zinc-50'
                  }`}
                  title="开启/关闭商品对比模式"
                >
                  <Scale className="w-3.5 h-3.5" />
                  <span>对比模式</span>
                  {compareProductIds.length > 0 && (
                    <span
                      className={`text-[10px] px-1.5 py-0.2 rounded-full font-bold ${
                        isCompareMode ? 'bg-zinc-900 text-white' : 'bg-amber-100 text-amber-900'
                      }`}
                    >
                      {compareProductIds.length}/3
                    </span>
                  )}
                </button>

                {/* Quick Restore Last Compare Shortcut if active compare is empty */}
                {compareProductIds.length === 0 && lastComparedProductsList.length > 0 && (
                  <button
                    onClick={handleRestoreLastCompare}
                    className="px-2.5 py-1.5 rounded-xl text-xs font-medium text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200/80 transition flex items-center gap-1 shadow-2xs"
                    title={`恢复上次对比组合（${lastComparedProductsList.map((p) => p.title).join(' vs ')}）`}
                  >
                    <RotateCcw className="w-3 h-3 text-amber-600" />
                    <span>恢复上次对比</span>
                  </button>
                )}
              </div>

              {/* Only In Stock Toggle */}
              <button
                onClick={() => setOnlyInStock(!onlyInStock)}
                className={`px-3 py-1.5 rounded-xl text-xs font-medium border transition flex items-center gap-1.5 ${
                  onlyInStock
                    ? 'bg-zinc-900 text-white border-zinc-900'
                    : 'bg-white text-zinc-600 border-zinc-200 hover:bg-zinc-50'
                }`}
              >
                <Check className={`w-3.5 h-3.5 ${onlyInStock ? 'opacity-100' : 'opacity-0'}`} />
                仅看现货
              </button>

              {/* Only Discount Toggle */}
              <button
                onClick={() => setOnlyDiscount(!onlyDiscount)}
                className={`px-3 py-1.5 rounded-xl text-xs font-medium border transition flex items-center gap-1.5 ${
                  onlyDiscount
                    ? 'bg-zinc-900 text-white border-zinc-900'
                    : 'bg-white text-zinc-600 border-zinc-200 hover:bg-zinc-50'
                }`}
              >
                <Check className={`w-3.5 h-3.5 ${onlyDiscount ? 'opacity-100' : 'opacity-0'}`} />
                限时特惠
              </button>

              {/* Sort Dropdown */}
              <div className="relative flex items-center">
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as SortOption)}
                  className="py-1.5 pl-3 pr-7 rounded-xl border border-zinc-200 bg-white text-xs font-medium text-zinc-800 focus:outline-none focus:border-zinc-900 cursor-pointer appearance-none"
                >
                  <option value="featured">综合推荐</option>
                  <option value="sales">销量最高</option>
                  <option value="price-asc">价格：低到高</option>
                  <option value="price-desc">价格：高到低</option>
                  <option value="rating">好评优先</option>
                  <option value="newest">新品上架</option>
                </select>
                <ArrowUpDown className="w-3.5 h-3.5 text-zinc-400 absolute right-2 pointer-events-none" />
              </div>

              {/* View Mode Switcher */}
              <div className="flex items-center border border-zinc-200 rounded-xl p-0.5 bg-zinc-50">
                <button
                  onClick={() => setViewMode('grid')}
                  className={`p-1.5 rounded-lg transition ${
                    viewMode === 'grid'
                      ? 'bg-white text-zinc-900 shadow-xs'
                      : 'text-zinc-400 hover:text-zinc-700'
                  }`}
                  title="网格视图"
                >
                  <LayoutGrid className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setViewMode('list')}
                  className={`p-1.5 rounded-lg transition ${
                    viewMode === 'list'
                      ? 'bg-white text-zinc-900 shadow-xs'
                      : 'text-zinc-400 hover:text-zinc-700'
                  }`}
                  title="列表视图"
                >
                  <List className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* Product Grid / List Section */}
        {filteredProducts.length === 0 ? (
          <div className="bg-white rounded-3xl border border-zinc-200 p-12 text-center space-y-4 shadow-xs">
            <div className="w-16 h-16 rounded-full bg-zinc-100 flex items-center justify-center text-zinc-400 mx-auto">
              <Search className="w-8 h-8 stroke-1" />
            </div>
            <div>
              <h3 className="text-base font-bold text-zinc-900">未找到符合条件的商品</h3>
              <p className="text-xs text-zinc-500 mt-1 max-w-sm mx-auto">
                尝试减少筛选条件，或者更换搜索关键词查找心仪的好物。
              </p>
            </div>
            <button
              onClick={() => {
                setSelectedCategory('all');
                setSearchQuery('');
                setOnlyInStock(false);
                setOnlyDiscount(false);
              }}
              className="px-5 py-2.5 rounded-xl bg-zinc-900 text-white text-xs font-semibold hover:bg-zinc-800 transition"
            >
              重置所有筛选
            </button>
          </div>
        ) : (
          <div
            className={
              viewMode === 'grid'
                ? 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 sm:gap-6'
                : 'space-y-4'
            }
          >
            {filteredProducts.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                viewMode={viewMode}
                isWishlisted={wishlist.includes(product.id)}
                isCompareMode={isCompareMode}
                isCompared={compareProductIds.includes(product.id)}
                onQuickView={openProduct}
                onAddToCart={(p) => handleAddToCart(p)}
                onToggleWishlist={handleToggleWishlist}
                onToggleCompare={handleToggleCompare}
              />
            ))}
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="mt-16 bg-white border-t border-zinc-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-12">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-10">
            {/* Brand Intro */}
            <div className="space-y-3">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-zinc-900 text-white flex items-center justify-center font-bold text-sm">
                  A
                </div>
                <span className="text-base font-bold tracking-tight text-zinc-900">Henfon商城</span>
              </div>
              <p className="text-xs text-zinc-500 leading-relaxed">
                Henfon商城致力于为注重生活品质的用户发掘极具设计美感与匠心工艺的数码、家居与出行精品。
              </p>
              <div className="flex items-center gap-2 text-xs text-emerald-600 font-semibold">
                <ShieldCheck className="w-4 h-4" />
                <span>100% 品牌官方正品授权</span>
              </div>
            </div>

            {/* Shopping Guide */}
            <div className="space-y-2.5 text-xs text-zinc-500">
              <h4 className="font-bold text-zinc-900 text-sm">购物指南</h4>
              <p className="hover:text-zinc-900 cursor-pointer">购物流程与支付说明</p>
              <p className="hover:text-zinc-900 cursor-pointer">顺丰包邮及配送时效</p>
              <p className="hover:text-zinc-900 cursor-pointer">优惠券使用规则</p>
              <p className="hover:text-zinc-900 cursor-pointer">发票开具与验真指南</p>
            </div>

            {/* Service & Guarantee */}
            <div className="space-y-2.5 text-xs text-zinc-500">
              <h4 className="font-bold text-zinc-900 text-sm">售后服务</h4>
              <p className="hover:text-zinc-900 cursor-pointer">7天无理由退货流程</p>
              <p className="hover:text-zinc-900 cursor-pointer">官方两年联保政策</p>
              <p className="hover:text-zinc-900 cursor-pointer">退款时效与退运险</p>
              <p className="hover:text-zinc-900 cursor-pointer">商品防伪真伪查询</p>
            </div>

            {/* Service Hotline */}
            <div className="space-y-3 p-4 rounded-2xl bg-zinc-50 border border-zinc-200/80">
              <h4 className="font-bold text-zinc-900 text-xs">客服专线 (7x24小时)</h4>
              <div className="text-xl font-black text-zinc-900 font-mono tracking-tight">
                400-880-9527
              </div>
              <p className="text-[11px] text-zinc-400">
                专属管家一对一为您提供选购与物流追踪服务
              </p>
            </div>
          </div>

          <div className="pt-8 border-t border-zinc-100 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-zinc-400">
            <div>
                © 2026 Henfon商城. All rights reserved.
            </div>
            <div className="flex gap-4">
              <span className="hover:text-zinc-600 cursor-pointer">用户协议</span>
              <span className="hover:text-zinc-600 cursor-pointer">隐私政策</span>
              <span className="hover:text-zinc-600 cursor-pointer">资质证照</span>
            </div>
          </div>
        </div>
      </footer>

      {/* Modals & Slide-Overs */}
      <ProductQuickView
        product={quickViewProduct}
        isWishlisted={quickViewProduct ? wishlist.includes(quickViewProduct.id) : false}
        onClose={() => setQuickViewProduct(null)}
        onAddToCart={(p, variants, qty) => {
          handleAddToCart(p, variants, qty);
          setQuickViewProduct(null);
        }}
        onDirectBuy={handleDirectBuy}
        onToggleWishlist={handleToggleWishlist}
      />

      <CartDrawer
        isOpen={isCartOpen}
        cartItems={cartItems}
        appliedCoupon={appliedCoupon}
        claimedCoupons={claimedCoupons}
        onClose={() => setIsCartOpen(false)}
        onUpdateQuantity={handleUpdateQuantity}
        onToggleSelectItem={handleToggleSelectItem}
        onToggleSelectAll={handleToggleSelectAll}
        onRemoveItem={handleRemoveCartItem}
        onClearCart={handleClearCart}
        onApplyCoupon={handleApplyCoupon}
        onRemoveCoupon={handleRemoveCoupon}
        onOpenCheckout={handleOpenCheckoutFromCart}
        onOpenCouponCenter={handleOpenCouponCenter}
      />

      <CheckoutModal
        isOpen={isCheckoutOpen}
        items={checkoutItems}
        appliedCoupon={appliedCoupon}
        claimedCoupons={claimedCoupons}
        onClose={() => setIsCheckoutOpen(false)}
        onPlaceOrderSuccess={handlePlaceOrderSuccess}
        onPersistOrder={handlePersistOrder}
        onPaymentFailure={(message) => showToast(message, 'error')}
        initialAddresses={memberAddresses}
        onPersistAddress={handlePersistAddress}
        onUpdateAddress={handleUpdateAddress}
        onDeleteAddress={handleDeleteAddress}
        onSetDefaultAddress={handleSetDefaultAddress}
        onApplyCoupon={handleApplyCoupon}
        onRemoveCoupon={handleRemoveCoupon}
      />

      <OrderSuccessModal
        order={completedOrder}
        onClose={() => setCompletedOrder(null)}
        onViewAllOrders={() => {
          setCompletedOrder(null);
          setIsOrdersOpen(true);
        }}
        onContinueShopping={() => setCompletedOrder(null)}
      />

      <OrdersModal
        isOpen={isOrdersOpen}
        orders={orders}
        onClose={() => setIsOrdersOpen(false)}
        onCancelOrder={handleCancelOrder}
        onConfirmOrder={handleConfirmOrder}
        afterSales={afterSales}
        afterSalesLoading={afterSalesLoading}
        afterSalesError={afterSalesError}
        onApplyAfterSale={handleApplyAfterSale}
        onCancelAfterSale={handleCancelAfterSale}
        onApplyInvoice={handleApplyInvoice}
      />

      <WishlistModal
        isOpen={isWishlistOpen}
        wishlistedProducts={wishlistedProductsList}
        onClose={() => setIsWishlistOpen(false)}
        onAddToCart={(p) => handleAddToCart(p)}
        onRemoveWishlist={handleToggleWishlist}
        onQuickView={(p) => {
          setIsWishlistOpen(false);
          openProduct(p);
        }}
      />

      {/* Floating Compare Bar */}
      <CompareFloatingBar
        isOpen={isCompareBarVisible}
        compareProducts={comparedProductsList}
        lastComparedProducts={lastComparedProductsList}
        compareHistory={compareHistoryWithProducts}
        onOpenModal={() => setIsCompareModalOpen(true)}
        onRemoveProduct={handleRemoveFromCompare}
        onClearAll={handleClearCompare}
        onCloseBar={handleCloseCompareBar}
        onRestoreLastCompare={handleRestoreLastCompare}
        onRestoreFromHistory={handleRestoreFromHistory}
        onDeleteHistoryItem={handleDeleteHistoryItem}
        onClearHistory={handleClearCompareHistory}
      />

      {/* Compare Details Modal */}
      <CompareModal
        isOpen={isCompareModalOpen}
        products={comparedProductsList}
        lastComparedProducts={lastComparedProductsList}
        onRestoreLastCompare={handleRestoreLastCompare}
        onClose={() => {
          if (compareProductIds.length > 0) {
            saveCompareHistory(compareProductIds);
          }
          setIsCompareModalOpen(false);
        }}
        onRemoveProduct={handleRemoveFromCompare}
        onAddToCart={(p, v, q) => {
          handleAddToCart(p, v, q);
        }}
        onBatchAddToCart={handleBatchAddToCart}
        onQuickView={(p) => {
          setIsCompareModalOpen(false);
          openProduct(p);
        }}
        onClearAll={() => {
          handleClearCompare();
          setIsCompareModalOpen(false);
        }}
      />

      {/* Login / Register Authentication Modal */}
      <AuthModal
        isOpen={isAuthModalOpen}
        initialMode={authModalMode}
        demoMode={DEMO_MODE}
        onClose={() => setIsAuthModalOpen(false)}
        onLoginSuccess={handleLoginSuccess}
      />

      {/* User Profile & Membership Modal */}
      <UserProfileModal
        isOpen={isUserProfileModalOpen}
        user={currentUser}
        claimedCoupons={claimedCoupons}
        onClose={() => setIsUserProfileModalOpen(false)}
        onUpdateUser={handleUpdateUser}
        onLogout={handleLogout}
        onOpenOrders={() => {
          setIsUserProfileModalOpen(false);
          setIsOrdersOpen(true);
        }}
        onOpenWishlist={() => {
          setIsUserProfileModalOpen(false);
          setIsWishlistOpen(true);
        }}
        onOpenCouponCenter={handleOpenCouponCenter}
      />
    </div>
  );
}
