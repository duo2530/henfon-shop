import React, { useEffect, useState } from 'react';
import { Building2, CheckCircle2, Clock3, Download, FileText, Mail, X, XCircle } from 'lucide-react';
import { Order } from '../types/ecommerce';
import { PortalInvoiceRecord } from '../api/portalApi';

interface InvoicePayload {
  invoiceType: 1 | 2;
  title: string;
  taxNo?: string;
  email?: string;
}

interface InvoiceModalProps {
  order: Order;
  existing?: PortalInvoiceRecord | null;
  defaultTitle?: string;
  defaultEmail?: string;
  onClose: () => void;
  onSubmit: (payload: InvoicePayload) => Promise<PortalInvoiceRecord>;
}

const statusMeta: Record<number, { label: string; tone: string; icon: React.ReactNode }> = {
  0: { label: '待开票', tone: 'amber', icon: <Clock3 className="h-4 w-4" /> },
  1: { label: '开票中', tone: 'sky', icon: <Clock3 className="h-4 w-4" /> },
  2: { label: '已开票', tone: 'emerald', icon: <CheckCircle2 className="h-4 w-4" /> },
  3: { label: '开票失败', tone: 'rose', icon: <XCircle className="h-4 w-4" /> },
  4: { label: '已取消', tone: 'zinc', icon: <XCircle className="h-4 w-4" /> },
};

/**
 * 展示订单发票申请表单及已有发票状态。
 *
 * @param order 当前订单
 * @param existing 已存在的发票记录
 * @param defaultTitle 默认发票抬头
 * @param defaultEmail 默认收票邮箱
 * @param onClose 关闭弹窗回调
 * @param onSubmit 提交发票申请回调
 * @author Henfon
 * @date 2026-09-03
 */
export const InvoiceModal: React.FC<InvoiceModalProps> = ({
  order,
  existing,
  defaultTitle = '个人',
  defaultEmail = '',
  onClose,
  onSubmit,
}) => {
  const [invoiceType, setInvoiceType] = useState<1 | 2>(existing?.invoiceType === 2 ? 2 : 1);
  const [title, setTitle] = useState(defaultTitle);
  const [taxNo, setTaxNo] = useState('');
  const [email, setEmail] = useState(defaultEmail);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setInvoiceType(existing?.invoiceType === 2 ? 2 : 1);
    setTitle(existing?.title || defaultTitle);
    setTaxNo(existing?.taxNo || '');
    setEmail(existing?.email || defaultEmail);
    setError(null);
  }, [defaultEmail, defaultTitle, existing]);

  const submit = async () => {
    if (!title.trim()) {
      setError('请填写发票抬头');
      return;
    }
    if (invoiceType === 2 && !taxNo.trim()) {
      setError('增值税专用发票需要填写纳税人识别号');
      return;
    }
    if (email.trim() && !/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError('请输入正确的接收邮箱');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        invoiceType,
        title: title.trim(),
        taxNo: invoiceType === 2 ? taxNo.trim() : undefined,
        email: email.trim() || undefined,
      });
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '发票申请失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  const meta = existing ? statusMeta[existing.status] || statusMeta[0] : null;
  const toneClasses = meta ? {
    amber: 'border-amber-200 bg-amber-50 text-amber-700',
    sky: 'border-sky-200 bg-sky-50 text-sky-700',
    emerald: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    rose: 'border-rose-200 bg-rose-50 text-rose-700',
    zinc: 'border-zinc-200 bg-zinc-100 text-zinc-600',
  }[meta.tone] : '';

  return (
    <div
      className="fixed inset-0 z-[80] flex items-start justify-center overflow-y-auto overscroll-contain bg-zinc-950/60 p-4 backdrop-blur-sm sm:items-center sm:p-6"
      onClick={() => !submitting && onClose()}
    >
      <div
        className="my-auto w-full max-w-lg overflow-hidden rounded-[28px] border border-white/70 bg-white shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="relative overflow-hidden bg-zinc-950 px-6 pb-6 pt-5 text-white sm:px-7">
          <div className="absolute -right-12 -top-16 h-36 w-36 rounded-full bg-amber-400/20 blur-2xl" />
          <div className="relative flex items-start justify-between gap-4">
            <div>
              <div className="mb-2 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.2em] text-amber-300">
                <FileText className="h-3.5 w-3.5" /> Invoice desk
              </div>
              <h2 className="text-xl font-bold tracking-tight">{existing ? '发票申请详情' : '申请电子发票'}</h2>
              <p className="mt-1 text-xs text-zinc-300">订单 {order.orderNumber} · 实付 ¥{order.totalPaid.toFixed(2)}</p>
            </div>
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              aria-label="关闭发票弹窗"
              className="rounded-xl p-2 text-zinc-400 transition hover:bg-white/10 hover:text-white disabled:opacity-50"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {existing && meta ? (
          <div className="space-y-5 p-6 sm:p-7">
            <div className={`flex items-center gap-3 rounded-2xl border px-4 py-3 ${toneClasses}`}>
              {meta.icon}
              <div>
                <p className="text-sm font-bold">{meta.label}</p>
                <p className="mt-0.5 text-[11px] opacity-80">申请单号：{existing.invoiceNo}</p>
              </div>
            </div>
            <div className="divide-y divide-zinc-100 rounded-2xl border border-zinc-200 bg-zinc-50/70 px-4 text-sm">
              <div className="flex items-center justify-between gap-4 py-3"><span className="text-zinc-500">发票类型</span><span className="font-semibold text-zinc-900">{existing.invoiceType === 2 ? '增值税专用发票' : '电子普通发票'}</span></div>
              <div className="flex items-center justify-between gap-4 py-3"><span className="text-zinc-500">发票抬头</span><span className="max-w-[65%] truncate font-semibold text-zinc-900">{existing.title}</span></div>
              {existing.email && <div className="flex items-center justify-between gap-4 py-3"><span className="text-zinc-500">接收邮箱</span><span className="max-w-[65%] truncate font-semibold text-zinc-900">{existing.email}</span></div>}
            </div>
            {existing.failureReason && <p className="rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-700">失败原因：{existing.failureReason}</p>}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button type="button" onClick={onClose} className="rounded-xl border border-zinc-200 px-4 py-2.5 text-sm font-semibold text-zinc-600 transition hover:bg-zinc-50">关闭</button>
              {existing.invoiceUrl && (
                <button type="button" onClick={() => window.open(existing.invoiceUrl, '_blank', 'noopener,noreferrer')} className="inline-flex items-center justify-center gap-2 rounded-xl bg-zinc-950 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-zinc-800"><Download className="h-4 w-4" />查看电子发票</button>
              )}
            </div>
          </div>
        ) : (
          <div className="space-y-5 p-6 sm:p-7">
            <div>
              <p className="mb-2 text-xs font-bold text-zinc-800">选择发票类型</p>
              <div className="grid grid-cols-2 gap-2 rounded-2xl bg-zinc-100 p-1">
                {([{ value: 1 as const, label: '电子普通发票', hint: '适合个人报销' }, { value: 2 as const, label: '增值税专用发票', hint: '适合企业报销' }]).map((option) => (
                  <button key={option.value} type="button" onClick={() => setInvoiceType(option.value)} className={`rounded-xl px-3 py-3 text-left transition ${invoiceType === option.value ? 'bg-white shadow-sm ring-1 ring-zinc-200' : 'text-zinc-500 hover:text-zinc-800'}`}>
                    <span className={`block text-xs font-bold ${invoiceType === option.value ? 'text-zinc-900' : ''}`}>{option.label}</span>
                    <span className="mt-1 block text-[10px] text-zinc-400">{option.hint}</span>
                  </button>
                ))}
              </div>
            </div>
            <label className="block text-xs font-bold text-zinc-700">发票抬头<span className="ml-1 text-rose-500">*</span>
              <div className="relative mt-1.5"><Building2 className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-zinc-400" /><input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="请输入个人姓名或公司名称" className="w-full rounded-xl border border-zinc-200 bg-white py-2.5 pl-9 pr-3 text-sm font-normal text-zinc-900 outline-none transition placeholder:text-zinc-400 focus:border-amber-400 focus:ring-2 focus:ring-amber-100" /></div>
            </label>
            {invoiceType === 2 && <label className="block text-xs font-bold text-zinc-700">纳税人识别号<span className="ml-1 text-rose-500">*</span>
              <input value={taxNo} onChange={(event) => setTaxNo(event.target.value)} placeholder="请输入统一社会信用代码" className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-white px-3 py-2.5 text-sm font-normal text-zinc-900 outline-none transition placeholder:text-zinc-400 focus:border-amber-400 focus:ring-2 focus:ring-amber-100" />
            </label>}
            <label className="block text-xs font-bold text-zinc-700">接收邮箱<span className="ml-1 font-normal text-zinc-400">（可选）</span>
              <div className="relative mt-1.5"><Mail className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-zinc-400" /><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="用于接收电子发票附件" className="w-full rounded-xl border border-zinc-200 bg-white py-2.5 pl-9 pr-3 text-sm font-normal text-zinc-900 outline-none transition placeholder:text-zinc-400 focus:border-amber-400 focus:ring-2 focus:ring-amber-100" /></div>
            </label>
            <div className="flex gap-2 rounded-xl bg-amber-50 px-3 py-2.5 text-[11px] leading-relaxed text-amber-800"><FileText className="mt-0.5 h-3.5 w-3.5 shrink-0" /><span>发票金额按订单实付金额开具，提交后由商家审核并开具。</span></div>
            {error && <p className="rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-700">{error}</p>}
            <div className="flex flex-col-reverse gap-2 border-t border-zinc-100 pt-4 sm:flex-row sm:justify-end">
              <button type="button" onClick={onClose} disabled={submitting} className="rounded-xl border border-zinc-200 px-4 py-2.5 text-sm font-semibold text-zinc-600 transition hover:bg-zinc-50 disabled:opacity-50">取消</button>
              <button type="button" onClick={() => void submit()} disabled={submitting} className="rounded-xl bg-zinc-950 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-zinc-900/10 transition hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-50">{submitting ? '提交中…' : '提交申请'}</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
