export interface ProductReview {
  id: string;
  userName: string;
  userAvatar: string;
  rating: number;
  date: string;
  comment: string;
  variantUsed?: string;
  helpfulCount: number;
}

export interface ProductVariant {
  name: string;
  options: {
    id: string;
    label: string;
    priceModifier?: number;
    image?: string;
  }[];
}

export interface ProductSku {
  id: number;
  skuCode: string;
  skuName: string;
  attributes: Record<string, string>;
  price: number;
  marketPrice: number;
  stock: number;
}

export interface Product {
  id: string;
  title: string;
  subtitle: string;
  category: 'digital' | 'home' | 'fashion' | 'audio' | 'lifestyle' | 'outdoor';
  categoryLabel: string;
  brand: string;
  price: number;
  originalPrice: number;
  rating: number;
  reviewCount: number;
  salesCount: number;
  stock: number;
  badge?: '热销' | '新品' | '直降' | '特惠' | '爆款';
  videoUrl?: string;
  videoPoster?: string;
  videoDuration?: string;
  images: string[];
  features: string[];
  specs: Record<string, string>;
  variants?: ProductVariant[];
  skus?: ProductSku[];
  description: string;
  isFreeShipping: boolean;
  deliveryEstimate: string;
}

export interface CartItem {
  id: string; // unique cart item id (product.id + variant options key)
  productId: string;
  skuId?: number;
  product: Product;
  selectedVariants: Record<string, string>; // e.g. { "颜色": "深空灰", "容量": "256GB" }
  quantity: number;
  unitPrice: number;
  selected: boolean;
}

export interface Address {
  id: string;
  receiverName: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
  tag?: '家' | '公司' | '学校';
  isDefault: boolean;
}

export interface Coupon {
  code: string;
  title: string;
  discountAmount: number;
  minSpend: number;
  expiresAt: string;
  description: string;
  tag?: string;
  category?: string;
  stockPercent?: number;
  highlight?: boolean;
}

export interface OrderItem {
  productId: string;
  skuId?: number;
  title: string;
  image: string;
  variantsSummary: string;
  price: number;
  quantity: number;
}

export type OrderStatus =
  | 'placed'
  | 'paid'
  | 'processing'
  | 'preparing'
  | 'shipped'
  | 'out_for_delivery'
  | 'delivered'
  | string;

export interface Order {
  id: string;
  orderNumber: string;
  trackingNumber: string;
  carrier?: string;
  createdAt: string;
  status: OrderStatus;
  statusLabel: string;
  items: OrderItem[];
  subtotal: number;
  discount: number;
  shippingFee: number;
  totalPaid: number;
  shippingAddress: Address;
  paymentMethod: string;
  estimatedDelivery: string;
  trackingSteps: {
    title: string;
    time: string;
    completed: boolean;
    description: string;
  }[];
}

export type SortOption = 'featured' | 'sales' | 'price-asc' | 'price-desc' | 'rating' | 'newest';

export interface FilterState {
  category: string;
  searchQuery: string;
  minPrice?: number;
  maxPrice?: number;
  onlyInStock: boolean;
  onlyDiscount: boolean;
  minRating: number;
  sortBy: SortOption;
}

export interface CompareHistoryItem {
  id: string;
  timestamp: number;
  productIds: string[];
}

export type MemberLevel = '普通会员' | '黄金VIP' | '黑金SVIP';

export interface UserProfile {
  id: string;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  avatar: string;
  memberLevel: MemberLevel;
  points: number;
  balance: number;
  couponsCount: number;
  joinedDate: string;
}
