import React, { useEffect, useState } from 'react';
import { useBodyScrollLock } from '../hooks/useBodyScrollLock';
import { Check, Copy, X } from 'lucide-react';
import QRCode from 'qrcode';
import { Order } from '../types/ecommerce';

interface PaymentModalProps {
  order: Order;
  onClose: () => void;
}

/**
 * 微信 Native 支付页面，展示支付单信息和可复制的二维码链接。
 *
 * @author Henfon
 * @date 2026-09-02
 * @description 支付回调完成前保持页面可见，并由父页面持续轮询支付状态。
 */
export const PaymentModal: React.FC<PaymentModalProps> = ({ order, onClose }) => {
  useBodyScrollLock();
  const codeUrl = order.paymentCodeUrl;
  const [qrDataUrl, setQrDataUrl] = useState('');

  useEffect(() => {
    let active = true;
    if (!codeUrl) {
      setQrDataUrl('');
      return () => { active = false; };
    }
    QRCode.toDataURL(codeUrl, { width: 240, margin: 2, errorCorrectionLevel: 'M' })
      .then((dataUrl) => {
        if (active) setQrDataUrl(dataUrl);
      })
      .catch(() => {
        if (active) setQrDataUrl('');
      });
    return () => { active = false; };
  }, [codeUrl]);

  const copyCodeUrl = async () => {
    if (!codeUrl) return;
    await navigator.clipboard?.writeText(codeUrl);
  };

  return (
    <div className="fixed inset-0 z-[70] flex items-start justify-center overflow-y-auto overscroll-contain bg-black/60 p-4 backdrop-blur-sm sm:items-center">
      <div className="relative my-auto max-h-[calc(100vh-2rem)] w-full max-w-md overflow-y-auto overscroll-contain rounded-3xl bg-white p-6 shadow-2xl">
        <button type="button" onClick={onClose} aria-label="关闭支付页面" className="absolute right-4 top-4 rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-900">
          <X className="h-5 w-5" />
        </button>
        <div className="text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
            <Check className="h-6 w-6" />
          </div>
          <h2 className="text-lg font-bold text-zinc-900">订单已创建，请完成支付</h2>
          <p className="mt-1 text-xs text-zinc-500">支付成功后页面会自动更新订单状态</p>
        </div>
        <div className="mt-5 space-y-2 rounded-2xl bg-zinc-50 p-4 text-sm">
          <div className="flex justify-between"><span className="text-zinc-500">订单号</span><span className="font-mono text-zinc-900">{order.orderNumber}</span></div>
          <div className="flex justify-between"><span className="text-zinc-500">应付金额</span><span className="font-bold text-zinc-900">¥{order.totalPaid.toFixed(2)}</span></div>
        </div>
        <div className="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50 p-4">
          <p className="text-xs font-semibold text-emerald-800">微信 Native 支付链接</p>
          {qrDataUrl && (
            <div className="mx-auto my-3 flex w-fit rounded-xl bg-white p-3 shadow-sm">
              <img src={qrDataUrl} alt="微信支付二维码" className="h-52 w-52" />
            </div>
          )}
          <p className="mt-2 break-all rounded-lg bg-white p-2 font-mono text-[10px] text-zinc-600">{codeUrl || '支付链接生成中，请稍候…'}</p>
          <button type="button" onClick={() => void copyCodeUrl()} disabled={!codeUrl} className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50">
            <Copy className="h-3.5 w-3.5" /> 复制支付链接
          </button>
        </div>
        <p className="mt-4 text-center text-[11px] text-zinc-400">请使用微信扫一扫二维码工具打开链接；支付完成后无需手动刷新。</p>
      </div>
    </div>
  );
};
