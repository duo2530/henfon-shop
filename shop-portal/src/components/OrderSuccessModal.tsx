import React from 'react';
import { useBodyScrollLock } from '../hooks/useBodyScrollLock';
import { Order } from '../types/ecommerce';
import { CheckCircle2, ArrowRight, MapPin, X } from 'lucide-react';
import { OrderTracking } from './OrderTracking';

interface OrderSuccessModalProps {
  order: Order | null;
  onClose: () => void;
  onViewAllOrders: () => void;
  onContinueShopping: () => void;
}

export const OrderSuccessModal: React.FC<OrderSuccessModalProps> = ({
  order,
  onClose,
  onViewAllOrders,
  onContinueShopping,
}) => {
  useBodyScrollLock(order != null);
  if (!order) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto overscroll-contain bg-black/60 backdrop-blur-xs flex items-start sm:items-center justify-center p-3 sm:p-6 animate-in fade-in duration-200">
      <div 
        className="bg-white rounded-3xl border border-zinc-200 shadow-2xl max-w-2xl w-full max-h-[calc(100vh-1.5rem)] sm:max-h-[90vh] overflow-y-auto overscroll-contain relative p-6 sm:p-8 space-y-6 animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 rounded-full bg-zinc-100 hover:bg-zinc-200 text-zinc-600 transition"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Success Header */}
        <div className="text-center space-y-2 pt-2">
          <div className="w-16 h-16 rounded-2xl bg-emerald-50 text-emerald-600 border border-emerald-200/60 mx-auto flex items-center justify-center shadow-xs">
            <CheckCircle2 className="w-8 h-8 text-emerald-600" />
          </div>
          <h2 className="text-2xl font-bold text-zinc-900 tracking-tight">支付成功！</h2>
          <p className="text-xs sm:text-sm text-zinc-500 max-w-md mx-auto">
            我们已收到您的订单并将尽快安排发货，感谢您选择Henfon商城。
          </p>
        </div>

        {/* Order Details Card */}
        <div className="p-4 rounded-2xl bg-zinc-50 border border-zinc-200/80 space-y-3 text-xs">
          <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-zinc-200/80">
            <div>
              <span className="text-zinc-400">订单编号：</span>
              <span className="font-mono font-bold text-zinc-800">{order.orderNumber}</span>
            </div>
            <div>
              <span className="text-zinc-400">运单号：</span>
              {order.trackingNumber ? (
                <span className="font-mono font-bold text-emerald-700">{order.trackingNumber}</span>
              ) : (
                <span className="font-semibold text-zinc-500">发货后由承运商分配</span>
              )}
            </div>
          </div>

          <div className="flex items-start gap-2 text-zinc-600">
            <MapPin className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
            <div>
              <span className="font-bold text-zinc-900">{order.shippingAddress.receiverName} ({order.shippingAddress.phone})</span>
              <p className="text-zinc-500 text-[11px] mt-0.5">
                {order.shippingAddress.province} {order.shippingAddress.city} {order.shippingAddress.district} {order.shippingAddress.detail}
              </p>
            </div>
          </div>

          <div className="flex justify-between items-center pt-2 border-t border-zinc-200/80 font-medium">
            <span className="text-zinc-500">实付金额 ({order.paymentMethod})</span>
            <span className="text-base font-black text-zinc-900">¥{order.totalPaid.toFixed(2)}</span>
          </div>
        </div>

        {/* Visual Shipment Timeline and Status Progress */}
        <div>
          <OrderTracking
            order={order}
            showCarrierCard={false}
            interactiveSimulator={true}
          />
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-3 pt-2">
          <button
            onClick={onViewAllOrders}
            className="flex-1 py-3 rounded-2xl border border-zinc-200 text-zinc-800 font-semibold text-xs hover:bg-zinc-50 transition"
          >
            查看我的订单
          </button>
          <button
            onClick={onContinueShopping}
            className="flex-1 py-3 rounded-2xl bg-zinc-900 text-white font-semibold text-xs hover:bg-zinc-800 transition flex items-center justify-center gap-1.5 shadow-md"
          >
            <span>继续逛逛</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
