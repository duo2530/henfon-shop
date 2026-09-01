import React, { useEffect, useState } from 'react';
import { CartItem, Address, Coupon, Order } from '../types/ecommerce';
import { INITIAL_ADDRESSES } from '../data/products';
import { quotePortalFreight } from '../api/portalApi';
import {
  X,
  MapPin,
  Plus,
  CreditCard,
  Truck,
  CheckCircle2,
  ShieldCheck,
  Building2,
  Home,
  Check,
  Ticket,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

interface CheckoutModalProps {
  isOpen: boolean;
  items: CartItem[];
  appliedCoupon: Coupon | null;
  claimedCoupons?: Coupon[];
  onClose: () => void;
  onPlaceOrderSuccess: (newOrder: Order, persistence?: CheckoutPersistenceResult) => void;
  onApplyCoupon?: (couponCode: string) => boolean;
  onRemoveCoupon?: () => void;
  onPersistOrder?: (order: Order) => Promise<CheckoutPersistenceResult | void>;
  onPaymentFailure?: (message: string) => void;
  initialAddresses?: Address[];
  onPersistAddress?: (address: Address) => Promise<void>;
  onUpdateAddress?: (address: Address) => Promise<void>;
  onDeleteAddress?: (addressId: string) => Promise<void>;
  onSetDefaultAddress?: (addressId: string) => Promise<void>;
}

/**
 * 门户结算持久化结果。
 *
 * @author Henfon
 * @date 2026-08-30
 * @description 描述后端订单和支付单创建结果，便于结算页区分真实联调与本地回退。
 */
export interface CheckoutPersistenceResult {
  serverOrderId?: number;
  serverOrderNo?: string;
  paymentNo?: string;
  paymentStatus?: number;
  paymentCreated?: boolean;
  message?: string;
}

export const CheckoutModal: React.FC<CheckoutModalProps> = ({
  isOpen,
  items = [],
  appliedCoupon,
  claimedCoupons = [],
  onClose,
  onPlaceOrderSuccess,
  onApplyCoupon,
  onRemoveCoupon,
  onPersistOrder,
  onPaymentFailure,
  initialAddresses,
  onPersistAddress,
  onUpdateAddress,
  onDeleteAddress,
  onSetDefaultAddress,
}) => {
  const safeItems = items || [];

  const [addresses, setAddresses] = useState<Address[]>(initialAddresses ?? INITIAL_ADDRESSES);
  const [selectedAddressId, setSelectedAddressId] = useState<string>(
    (initialAddresses?.[0] || INITIAL_ADDRESSES[0])?.id || ''
  );
  const [isAddingAddress, setIsAddingAddress] = useState(false);
  const [editingAddressId, setEditingAddressId] = useState<string | null>(null);
  const [showCouponSelector, setShowCouponSelector] = useState(false);

  // New Address form
  const [newReceiver, setNewReceiver] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [newRegion, setNewRegion] = useState('北京市 朝阳区');
  const [newDetail, setNewDetail] = useState('');
  const [newTag, setNewTag] = useState<'家' | '公司' | '学校'>('家');

  // Delivery & Payment Settings
  const [deliveryTime, setDeliveryTime] = useState('工作日、双休日均可配送');
  const [paymentMethod, setPaymentMethod] = useState<'wechat'>('wechat');
  const [needInvoice, setNeedInvoice] = useState(false);
  const [orderNotes, setOrderNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [shippingFee, setShippingFee] = useState(0);
  const [freightQuoteLoading, setFreightQuoteLoading] = useState(false);

  useEffect(() => {
    if (initialAddresses) {
      setAddresses(initialAddresses);
      setSelectedAddressId(initialAddresses.find((address) => address.isDefault)?.id || initialAddresses[0]?.id || '');
    }
  }, [initialAddresses]);

  const selectedAddress =
    addresses.find((a) => a.id === selectedAddressId) || addresses[0];

  const rawSubtotal = safeItems.reduce(
    (acc, item) => acc + (item.unitPrice || 0) * (item.quantity || 1),
    0
  );
  const couponDiscount = appliedCoupon
    ? rawSubtotal >= appliedCoupon.minSpend
      ? appliedCoupon.discountAmount
      : 0
    : 0;

  const fallbackShippingFee = rawSubtotal > 0 ? (rawSubtotal >= 99 ? 0 : 15) : 0;
  const freightItemsKey = safeItems
    .map((item) => `${item.productId}:${item.skuId || ''}:${item.quantity}`)
    .join('|');

  useEffect(() => {
    if (!isOpen || !selectedAddress || rawSubtotal <= 0) {
      setShippingFee(0);
      setFreightQuoteLoading(false);
      return;
    }
    let active = true;
    setFreightQuoteLoading(true);
    quotePortalFreight({
      items: safeItems.map((item) => ({
        productId: Number(item.productId.replace(/^prod-/, '')),
        skuId: item.skuId,
        quantity: item.quantity,
      })),
      receiverProvince: selectedAddress.province,
      receiverCity: selectedAddress.city,
      receiverDistrict: selectedAddress.district,
      subtotalAmount: rawSubtotal,
      discountAmount: couponDiscount,
    }).then((quote) => {
      if (active) setShippingFee(Number(quote.freightAmount || 0));
    }).catch(() => {
      // 后端暂不可用时保留开发环境兜底规则，创建订单仍会由后端校验真实模板金额。
      if (active) setShippingFee(fallbackShippingFee);
    }).finally(() => {
      if (active) setFreightQuoteLoading(false);
    });
    return () => {
      active = false;
    };
  }, [isOpen, selectedAddress?.id, selectedAddress?.province, selectedAddress?.city,
    selectedAddress?.district, rawSubtotal, couponDiscount, freightItemsKey]);

  const totalPayable = Math.max(0, rawSubtotal - couponDiscount + shippingFee);

  if (!isOpen) return null;

  const resetAddressForm = () => {
    setNewReceiver('');
    setNewPhone('');
    setNewRegion('北京市 朝阳区');
    setNewDetail('');
    setNewTag('家');
    setEditingAddressId(null);
    setIsAddingAddress(false);
  };

  const handleEditAddress = (address: Address) => {
    setEditingAddressId(address.id);
    setNewReceiver(address.receiverName);
    setNewPhone(address.phone);
    setNewRegion([address.province, address.city, address.district].filter(Boolean).join(' '));
    setNewDetail(address.detail);
    setNewTag(address.tag || '家');
    setIsAddingAddress(true);
  };

  const handleSaveAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newReceiver || !newPhone || !newDetail) return;

    const editingAddress = editingAddressId ? addresses.find((address) => address.id === editingAddressId) : undefined;
    const nextAddress: Address = {
      id: editingAddress?.id || `addr-${Date.now()}`,
      receiverName: newReceiver,
      phone: newPhone,
      province: newRegion.split(' ')[0] || '北京市',
      city: newRegion.split(' ')[1] || '北京市',
      district: newRegion.split(' ')[2] || '朝阳区',
      detail: newDetail,
      tag: newTag,
      isDefault: false,
    };

    const previousAddresses = addresses;
    const finalAddress = editingAddress ? { ...editingAddress, ...nextAddress } : nextAddress;
    setAddresses(editingAddress
      ? addresses.map((address) => address.id === editingAddress.id ? finalAddress : address)
      : [finalAddress, ...addresses]);
    try {
      if (editingAddress) await onUpdateAddress?.(finalAddress);
      else await onPersistAddress?.(finalAddress);
    } catch (error) {
      setAddresses(previousAddresses);
      console.warn('收货地址同步失败，已恢复原地址', error);
      return;
    }
    setSelectedAddressId(finalAddress.id);
    resetAddressForm();
  };

  const handleSetDefaultAddress = async (addressId: string) => {
    const previousAddresses = addresses;
    setAddresses(addresses.map((address) => ({ ...address, isDefault: address.id === addressId })));
    setSelectedAddressId(addressId);
    try {
      await onSetDefaultAddress?.(addressId);
    } catch (error) {
      setAddresses(previousAddresses);
      console.warn('默认地址同步失败，已恢复原默认地址', error);
    }
  };

  const handleDeleteAddress = async (addressId: string) => {
    const address = addresses.find((item) => item.id === addressId);
    if (!address || !window.confirm(`确定删除收货地址“${address.receiverName}”吗？`)) return;
    const previousAddresses = addresses;
    const previousSelectedAddressId = selectedAddressId;
    const nextAddresses = addresses.filter((item) => item.id !== addressId);
    setAddresses(nextAddresses);
    if (selectedAddressId === addressId) {
      setSelectedAddressId(nextAddresses.find((item) => item.isDefault)?.id || nextAddresses[0]?.id || '');
    }
    try {
      await onDeleteAddress?.(addressId);
    } catch (error) {
      setAddresses(previousAddresses);
      setSelectedAddressId(previousSelectedAddressId);
      console.warn('收货地址删除失败，已恢复原地址', error);
    }
  };

  const handlePayOrder = async () => {
    if (!selectedAddress || isSubmitting) return;
    setIsSubmitting(true);

    const orderNumber = `AO${Date.now()}`;
    const trackingNumber = `SF${Math.floor(1000000000 + Math.random() * 9000000000)}`;
    const orderItems = items.map((it) => ({
      productId: it.productId,
      skuId: it.skuId,
      title: it.product.title,
      image: it.product.images[0],
      variantsSummary: Object.entries(it.selectedVariants)
        .map(([k, v]) => `${k}:${v}`)
        .join(' / '),
      price: it.unitPrice,
      quantity: it.quantity,
    }));

    const paymentMethodNames: Record<string, string> = {
      wechat: '微信支付',
    };

    // 真实支付回调尚未完成前，订单保持待付款，避免前端提前展示已付款。
    const newOrder: Order = {
      id: `ord-${Date.now()}`,
      orderNumber,
      trackingNumber,
      createdAt: new Date().toLocaleString('zh-CN', { hour12: false }),
      status: 'placed',
      statusLabel: '待付款',
      items: orderItems,
      subtotal: rawSubtotal,
      discount: couponDiscount,
      shippingFee,
      totalPaid: totalPayable,
      shippingAddress: selectedAddress,
      paymentMethod: paymentMethodNames[paymentMethod] || '在线支付',
      flashSaleId: items.length === 1 ? items[0].flashSaleId : undefined,
      estimatedDelivery: '预计 1-2 日内顺丰送达',
      trackingSteps: [
        {
          title: '订单提交成功',
          time: '刚刚',
          completed: true,
          description: '订单已创建，等待完成支付。',
        },
        {
          title: '支付成功，等待仓库拣货',
          time: '待支付回调',
          completed: false,
          description: '支付完成后系统将自动下发配货指令。',
        },
        {
          title: '顺丰速运揽收',
          time: '待支付',
          completed: false,
          description: '支付成功后安排顺丰速运揽件。',
        },
        {
          title: '干线运输与派送',
          time: '待支付',
          completed: false,
          description: '支付成功后进入物流运输流程。',
        },
      ],
    };

    try {
      const persistence = await onPersistOrder?.(newOrder);
      if (persistence?.serverOrderId) newOrder.id = String(persistence.serverOrderId);
      if (persistence?.serverOrderNo) newOrder.orderNumber = persistence.serverOrderNo;
      if (!persistence?.paymentCreated) {
        onPaymentFailure?.(persistence?.message || '支付单创建失败，订单已保留在本地，请稍后重试');
      }
      onPlaceOrderSuccess(newOrder, persistence);
    } catch (error) {
      console.error('订单或支付单同步失败', error);
      onPaymentFailure?.(error instanceof Error ? error.message : '支付单创建失败，订单已保留在本地，请稍后重试');
      onPlaceOrderSuccess(newOrder);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/60 backdrop-blur-xs flex items-center justify-center p-3 sm:p-6 animate-in fade-in duration-200">
      <div 
        className="bg-white rounded-3xl border border-zinc-200 shadow-2xl max-w-3xl w-full overflow-hidden relative flex flex-col max-h-[94vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-5 sm:p-6 border-b border-zinc-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-zinc-900">确认订单并结算</h2>
            <p className="text-xs text-zinc-500">请核对收货地址与商品规格信息</p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-zinc-100 text-zinc-500 hover:text-zinc-900 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body Scroll */}
        <div className="overflow-y-auto p-5 sm:p-6 space-y-6 flex-1">
          {/* 1. Address Section */}
          <section className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-zinc-900 flex items-center gap-2">
                <MapPin className="w-4 h-4 text-rose-500" />
                收货地址
              </h3>
              <button
                onClick={() => {
                  if (isAddingAddress) resetAddressForm();
                  else {
                    setEditingAddressId(null);
                    setIsAddingAddress(true);
                  }
                }}
                className="text-xs font-semibold text-zinc-800 hover:text-zinc-950 flex items-center gap-1"
              >
                <Plus className="w-3.5 h-3.5" />
                {isAddingAddress ? '取消编辑' : '新增收货地址'}
              </button>
            </div>

            {/* Inline Add Address Form */}
            {isAddingAddress && (
              <form
                onSubmit={handleSaveAddress}
                className="p-4 rounded-2xl bg-zinc-50 border border-zinc-200 space-y-3 animate-in fade-in"
              >
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="text-[11px] font-semibold text-zinc-500 block mb-1">
                      收货人姓名
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="例如：张先生"
                      value={newReceiver}
                      onChange={(e) => setNewReceiver(e.target.value)}
                      className="w-full py-1.5 px-3 rounded-xl border border-zinc-200 bg-white text-xs text-zinc-900 focus:outline-none focus:border-zinc-900"
                    />
                  </div>
                  <div>
                    <label className="text-[11px] font-semibold text-zinc-500 block mb-1">
                      手机号码
                    </label>
                    <input
                      type="tel"
                      required
                      placeholder="11位手机号"
                      value={newPhone}
                      onChange={(e) => setNewPhone(e.target.value)}
                      className="w-full py-1.5 px-3 rounded-xl border border-zinc-200 bg-white text-xs text-zinc-900 focus:outline-none focus:border-zinc-900"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-[11px] font-semibold text-zinc-500 block mb-1">
                    所在地区
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="省 / 市 / 区"
                    value={newRegion}
                    onChange={(e) => setNewRegion(e.target.value)}
                    className="w-full py-1.5 px-3 rounded-xl border border-zinc-200 bg-white text-xs text-zinc-900 focus:outline-none focus:border-zinc-900"
                  />
                </div>

                <div>
                  <label className="text-[11px] font-semibold text-zinc-500 block mb-1">
                    详细地址（街道、门牌号等）
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="例如：科技园中路1号 创新大厦A座801"
                    value={newDetail}
                    onChange={(e) => setNewDetail(e.target.value)}
                    className="w-full py-1.5 px-3 rounded-xl border border-zinc-200 bg-white text-xs text-zinc-900 focus:outline-none focus:border-zinc-900"
                  />
                </div>

                <div className="flex items-center justify-between pt-1">
                  <div className="flex items-center gap-2">
                    <span className="text-[11px] font-medium text-zinc-500">地址标签:</span>
                    {(['家', '公司', '学校'] as const).map((tag) => (
                      <button
                        type="button"
                        key={tag}
                        onClick={() => setNewTag(tag)}
                        className={`px-2.5 py-0.5 rounded-lg text-xs font-medium border transition ${
                          newTag === tag
                            ? 'bg-zinc-900 text-white border-zinc-900'
                            : 'bg-white text-zinc-600 border-zinc-200'
                        }`}
                      >
                        {tag}
                      </button>
                    ))}
                  </div>

                  <button
                    type="submit"
                    className="px-4 py-1.5 rounded-xl bg-zinc-900 text-white text-xs font-semibold hover:bg-zinc-800 transition"
                  >
                    {editingAddressId ? '保存修改并使用' : '保存并使用'}
                  </button>
                </div>
              </form>
            )}

            {/* Address Cards Selector */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {addresses.map((addr) => {
                const isSelected = selectedAddressId === addr.id;
                return (
                  <div
                    key={addr.id}
                    onClick={() => setSelectedAddressId(addr.id)}
                    className={`p-3.5 rounded-2xl border cursor-pointer transition-all ${
                      isSelected
                        ? 'border-zinc-900 bg-zinc-50 ring-1 ring-zinc-900/10 shadow-xs'
                        : 'border-zinc-200 bg-white hover:border-zinc-300'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2 mb-1.5">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-zinc-900">
                          {addr.receiverName}
                        </span>
                        <span className="text-xs text-zinc-500 font-mono">
                          {addr.phone}
                        </span>
                      </div>
                      {addr.tag && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-zinc-200 text-zinc-700 font-medium">
                          {addr.tag}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-zinc-600 leading-snug">
                      {addr.province} {addr.city} {addr.district} {addr.detail}
                    </p>
                    <div className="flex items-center justify-between gap-2 mt-2 pt-2 border-t border-zinc-100">
                      <div className="flex items-center gap-2">
                        {addr.isDefault ? (
                          <span className="text-[10px] font-semibold text-emerald-700">默认地址</span>
                        ) : (
                          <button
                            type="button"
                            onClick={(event) => {
                              event.stopPropagation();
                              void handleSetDefaultAddress(addr.id);
                            }}
                            className="text-[10px] font-semibold text-zinc-500 hover:text-zinc-900"
                          >
                            设为默认
                          </button>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            handleEditAddress(addr);
                          }}
                          className="text-[10px] font-semibold text-zinc-500 hover:text-zinc-900"
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            void handleDeleteAddress(addr.id);
                          }}
                          className="text-[10px] font-semibold text-rose-500 hover:text-rose-700"
                        >
                          删除
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          {/* 2. Order Items Review */}
          <section className="space-y-3">
            <h3 className="text-sm font-bold text-zinc-900">商品清单 ({items.length}件)</h3>
            <div className="border border-zinc-200 rounded-2xl divide-y divide-zinc-100 overflow-hidden">
              {items.map((item) => (
                <div key={item.id} className="p-3 bg-white flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <img
                      src={item.product.images[0]}
                      alt={item.product.title}
                      className="w-12 h-12 rounded-xl object-cover bg-zinc-100 border border-zinc-200 shrink-0"
                    />
                    <div className="min-w-0">
                      <h4 className="text-xs font-semibold text-zinc-900 truncate">
                        {item.product.title}
                      </h4>
                      <p className="text-[11px] text-zinc-400">
                        {Object.entries(item.selectedVariants).map(([k, v]) => `${k}:${v}`).join(' ')} × {item.quantity}
                      </p>
                    </div>
                  </div>
                  <div className="text-xs font-bold text-zinc-900 shrink-0">
                    ¥{(item.unitPrice * item.quantity).toFixed(2)}
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* 3. Coupon & Discount Selection */}
          <section className="p-4 rounded-2xl bg-amber-50/50 border border-amber-200/80 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-lg bg-amber-600 text-white flex items-center justify-center">
                  <Ticket className="w-3.5 h-3.5" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-zinc-900">
                    优惠券折扣
                    {appliedCoupon && (
                      <span className="ml-2 text-[10px] px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-bold">
                        已自动关联立减 ¥{appliedCoupon.discountAmount}
                      </span>
                    )}
                  </h4>
                  <p className="text-[11px] text-zinc-500">
                    {appliedCoupon ? `已应用「${appliedCoupon.title}」满¥${appliedCoupon.minSpend}减¥${appliedCoupon.discountAmount}` : '当前无适用优惠券'}
                  </p>
                </div>
              </div>

              {claimedCoupons.length > 0 && (
                <button
                  type="button"
                  onClick={() => setShowCouponSelector(!showCouponSelector)}
                  className="text-xs text-amber-800 hover:text-amber-950 font-bold flex items-center gap-1 transition"
                >
                  <span>切换优惠券 ({claimedCoupons.length}张可用)</span>
                  {showCouponSelector ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                </button>
              )}
            </div>

            {/* Collapsible Coupon List */}
            {showCouponSelector && (
              <div className="pt-2 border-t border-amber-200/60 space-y-2 animate-in fade-in duration-200">
                <div className="space-y-1.5 max-h-40 overflow-y-auto">
                  {claimedCoupons.map((c) => {
                    const isEligible = rawSubtotal >= c.minSpend;
                    const isSelected = appliedCoupon?.code === c.code;

                    return (
                      <div
                        key={c.code}
                        onClick={() => {
                          if (isEligible && onApplyCoupon) {
                            if (isSelected && onRemoveCoupon) {
                              onRemoveCoupon();
                            } else {
                              onApplyCoupon(c.code);
                            }
                          }
                        }}
                        className={`p-2.5 rounded-xl border flex items-center justify-between text-xs cursor-pointer transition ${
                          isSelected
                            ? 'bg-emerald-50 border-emerald-300 text-emerald-900 font-bold shadow-xs'
                            : isEligible
                            ? 'bg-white border-zinc-200 hover:border-amber-300 text-zinc-800'
                            : 'bg-zinc-100/60 border-zinc-200 text-zinc-400 cursor-not-allowed opacity-60'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <div className={`w-4 h-4 rounded-full flex items-center justify-center text-[10px] ${isSelected ? 'bg-emerald-600 text-white' : 'border border-zinc-300'}`}>
                            {isSelected && <Check className="w-2.5 h-2.5 stroke-3" />}
                          </div>
                          <div>
                            <span className="font-bold text-rose-600 mr-1.5">¥{c.discountAmount}</span>
                            <span>{c.title}</span>
                            <span className="text-[10px] text-zinc-400 ml-1.5">(满¥{c.minSpend})</span>
                          </div>
                        </div>
                        <div>
                          {isSelected ? (
                            <span className="text-[10px] text-emerald-700 font-bold">使用中</span>
                          ) : isEligible ? (
                            <span className="text-[10px] text-amber-700 font-semibold">点击使用</span>
                          ) : (
                            <span className="text-[10px] text-zinc-400">还差¥{(c.minSpend - rawSubtotal).toFixed(0)}</span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </section>

          {/* 4. Delivery & Payment Method */}
          <section className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Delivery Preferences */}
            <div className="p-4 rounded-2xl bg-zinc-50 border border-zinc-200 space-y-3">
              <h4 className="text-xs font-bold text-zinc-900 flex items-center gap-1.5">
                <Truck className="w-3.5 h-3.5 text-emerald-600" />
                配送时间与备注
              </h4>
              <select
                value={deliveryTime}
                onChange={(e) => setDeliveryTime(e.target.value)}
                className="w-full py-2 px-3 rounded-xl border border-zinc-200 bg-white text-xs text-zinc-800 focus:outline-none"
              >
                <option>工作日、双休日均可配送</option>
                <option>仅工作日送达 (周一至周五)</option>
                <option>仅双休日送达 (周六至周日)</option>
              </select>

              <input
                type="text"
                placeholder="订单留言或特殊要求 (选填)"
                value={orderNotes}
                onChange={(e) => setOrderNotes(e.target.value)}
                className="w-full py-2 px-3 rounded-xl border border-zinc-200 bg-white text-xs text-zinc-800 placeholder:text-zinc-400 focus:outline-none"
              />
            </div>

            {/* Payment Method Selector */}
            <div className="p-4 rounded-2xl bg-zinc-50 border border-zinc-200 space-y-3">
              <h4 className="text-xs font-bold text-zinc-900 flex items-center gap-1.5">
                <CreditCard className="w-3.5 h-3.5 text-sky-600" />
                选择支付方式
              </h4>

              <div className="grid grid-cols-2 gap-2">
                {[
                  { id: 'wechat', label: '微信支付', desc: '亿万用户的选择' },
                ].map((m) => (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => setPaymentMethod(m.id as any)}
                    className={`p-2.5 rounded-xl border text-left transition-all ${
                      paymentMethod === m.id
                        ? 'bg-zinc-900 text-white border-zinc-900 shadow-xs'
                        : 'bg-white text-zinc-800 border-zinc-200 hover:border-zinc-300'
                    }`}
                  >
                    <div className="text-xs font-bold">{m.label}</div>
                    <div className={`text-[10px] ${paymentMethod === m.id ? 'text-zinc-300' : 'text-zinc-400'}`}>
                      {m.desc}
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </section>
        </div>

        {/* Footer with Price breakdown & Submit button */}
        <div className="p-5 sm:p-6 border-t border-zinc-200 bg-white flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-baseline gap-3 text-xs text-zinc-500">
            <span>
              商品总额：<strong>¥{rawSubtotal.toFixed(2)}</strong>
            </span>
            {couponDiscount > 0 && (
              <span className="text-emerald-600">
                优惠立减：<strong>-¥{couponDiscount.toFixed(2)}</strong>
              </span>
            )}
            <span>
              顺丰运费：<strong>{shippingFee === 0 ? '免费' : `¥${shippingFee}`}</strong>
            </span>
          </div>

          <div className="flex items-center gap-4 w-full sm:w-auto justify-between sm:justify-end">
            <div className="text-right">
              <span className="text-xs text-zinc-500">实付款: </span>
              <span className="text-2xl font-black text-zinc-900">
                ¥{totalPayable.toFixed(2)}
              </span>
            </div>

            <button
              onClick={handlePayOrder}
              disabled={isSubmitting || freightQuoteLoading || !selectedAddress}
              className="py-3 px-8 rounded-2xl bg-zinc-900 text-white font-bold text-sm hover:bg-zinc-800 disabled:opacity-50 transition shadow-lg flex items-center justify-center gap-2"
            >
              {freightQuoteLoading ? (
                <>
                  <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin"></span>
                  <span>计算运费中...</span>
                </>
              ) : isSubmitting ? (
                <>
                  <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin"></span>
                  <span>安全支付中...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="w-4 h-4 text-emerald-400" />
                  <span>立即支付 ¥{totalPayable.toFixed(2)}</span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
