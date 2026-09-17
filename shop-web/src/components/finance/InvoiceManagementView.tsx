import React, { useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { Download, CheckCircle, Clock, Search, Send } from 'lucide-react';
import { BackendPaymentInvoice, listPaymentInvoices, updatePaymentInvoiceStatus, uploadStorageFile } from '../../api/adminApi';
import { formatDateTime } from '../../utils/datetime';

export interface InvoiceRecord {
  id: string;
  invoiceNo: string;
  orderNumber: string;
  type: 'special_vat' | 'general_vat' | 'electronic';
  title: string;
  taxCode: string;
  amount: number;
  /** 当前发票接口只返回含税金额，税额待开票平台回传后展示。 */
  taxAmount?: number;
  applicantEmail: string;
  status: 'issued' | 'pending' | 'issuing' | 'rejected' | 'red_ink';
  createdAt: string;
  issuedAt?: string;
  invoiceUrl?: string;
}

function toInvoiceRecord(invoice: BackendPaymentInvoice): InvoiceRecord {
  const status: InvoiceRecord['status'] = invoice.status === 2
    ? 'issued'
    : invoice.status === 1
      ? 'issuing'
      : invoice.status === 3 || invoice.status === 4
        ? 'rejected'
        : 'pending';
  return {
    id: String(invoice.id),
    invoiceNo: invoice.invoiceNo,
    orderNumber: invoice.orderNo,
    type: invoice.invoiceType === 2 ? 'special_vat' : 'general_vat',
    title: invoice.title,
    taxCode: invoice.taxNo || '-',
    amount: Number(invoice.amount || 0),
    applicantEmail: invoice.email || '-',
    status,
    createdAt: formatDateTime(invoice.createdAt || invoice.requestedAt, '-'),
    issuedAt: formatDateTime(invoice.issuedAt, '-'),
    invoiceUrl: invoice.invoiceUrl,
  };
}

export const InvoiceManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [invoices, setInvoices] = useState<InvoiceRecord[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadInvoices = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const result = await listPaymentInvoices({ keyword: searchTerm || undefined });
      setInvoices(result.records.map(toInvoiceRecord));
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '发票数据加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const timer = window.setTimeout(() => void loadInvoices(), 250);
    return () => window.clearTimeout(timer);
  }, [searchTerm]);

  const handleIssueInvoice = async (invoiceNo: string) => {
    try {
      await updatePaymentInvoiceStatus(invoiceNo, { status: 1 });
      showToast('发票已进入开票中状态，待开票平台回传结果', 'success');
      await loadInvoices();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '发票状态更新失败', 'error');
    }
  };

  const handleBatchIssue = async () => {
    const pendingInvoices = invoices.filter((invoice) => invoice.status === 'pending');
    if (pendingInvoices.length === 0) {
      showToast('当前没有待开票申请', 'info');
      return;
    }
    try {
      await Promise.all(pendingInvoices.map((invoice) => updatePaymentInvoiceStatus(invoice.invoiceNo, { status: 1 })));
      showToast(`已将 ${pendingInvoices.length} 条申请置为开票中`, 'success');
      await loadInvoices();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '批量更新发票状态失败', 'error');
      await loadInvoices();
    }
  };

  const handleUploadInvoice = async (invoice: InvoiceRecord, file?: File) => {
    if (!file) return;
    try {
      const uploaded = await uploadStorageFile(file);
      await updatePaymentInvoiceStatus(invoice.invoiceNo, { status: 2, invoiceUrl: uploaded.url });
      showToast('发票附件已关联并标记为已开具', 'success');
      await loadInvoices();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '发票附件上传失败', 'error');
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              发票与税务管理 (Invoice & Tax Management)
            </h2>
            <span className="text-xs bg-blue-50 text-blue-700 font-semibold px-2 py-0.5 rounded-full border border-blue-200">
              金税四期
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            企业增值税专用发票、普通电子发票审核、一键开票税盘开具及红字冲红。
          </p>
        </div>

        <button
          onClick={() => void handleBatchIssue()}
          className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs"
        >
          <Send className="w-4 h-4" />
          <span>批量进入开票中</span>
        </button>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50">
          <div className="relative max-w-xs">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="搜索发票抬头、税号或订单号..."
              className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none"
            />
          </div>
        </div>

        {loadError && (
          <div className="m-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <div className="flex items-center justify-between gap-3">
              <span>{loadError}</span>
              <button onClick={() => void loadInvoices()} className="font-semibold underline">重试</button>
            </div>
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase">
              <tr>
                <th className="py-3 px-4">发票号码 / 订单号</th>
                <th className="py-3 px-4">发票类型</th>
                <th className="py-3 px-4">抬头与纳税人识别号</th>
                <th className="py-3 px-4 text-right">开票金额 (¥)</th>
                <th className="py-3 px-4 text-right">税额</th>
                <th className="py-3 px-4 text-center">状态</th>
                <th className="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-sm">
              {loading ? (
                <tr><td colSpan={7} className="py-10 text-center text-gray-400">正在加载发票数据...</td></tr>
              ) : invoices.length === 0 ? (
                <tr><td colSpan={7} className="py-10 text-center text-gray-400">暂无发票申请</td></tr>
              ) : invoices.map((inv) => (
                <tr key={inv.id} className="hover:bg-[#F8FAFC]">
                  <td className="py-3 px-4">
                    <div className="font-mono font-bold text-gray-900 text-xs">{inv.invoiceNo}</div>
                    <div className="text-xs text-blue-600">{inv.orderNumber}</div>
                  </td>

                  <td className="py-3 px-4">
                    {inv.type === 'special_vat' ? (
                      <span className="text-xs font-semibold text-purple-700 bg-purple-50 px-2 py-0.5 rounded">
                        增值税专票
                      </span>
                    ) : (
                      <span className="text-xs font-semibold text-gray-700 bg-gray-100 px-2 py-0.5 rounded">
                        增值税电票
                      </span>
                    )}
                  </td>

                  <td className="py-3 px-4">
                    <div className="font-bold text-gray-800 text-xs">{inv.title}</div>
                    <div className="font-mono text-[11px] text-gray-400">{inv.taxCode}</div>
                  </td>

                  <td className="py-3 px-4 text-right font-mono font-bold text-gray-900">
                    ¥{inv.amount.toFixed(2)}
                  </td>

                  <td className="py-3 px-4 text-right font-mono text-gray-500 text-xs">
                    {inv.taxAmount === undefined ? '待平台返回' : `¥${inv.taxAmount.toFixed(2)}`}
                  </td>

                  <td className="py-3 px-4 text-center">
                    {inv.status === 'issued' ? (
                      <span className="text-xs font-semibold text-emerald-800 bg-emerald-100 px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                        <CheckCircle className="w-3 h-3" /> 已开具
                      </span>
                    ) : inv.status === 'issuing' ? (
                      <span className="text-xs font-semibold text-blue-800 bg-blue-100 px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                        <Clock className="w-3 h-3" /> 开票中
                      </span>
                    ) : inv.status === 'rejected' ? (
                      <span className="text-xs font-semibold text-red-800 bg-red-100 px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                        <Clock className="w-3 h-3" /> 失败/取消
                      </span>
                    ) : (
                      <span className="text-xs font-semibold text-amber-800 bg-amber-100 px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                        <Clock className="w-3 h-3" /> 待开具
                      </span>
                    )}
                  </td>

                  <td className="py-3 px-4 text-right">
                    {inv.status === 'pending' ? (
                      <button
                        onClick={() => void handleIssueInvoice(inv.invoiceNo)}
                        className="px-2.5 py-1 bg-blue-600 text-white rounded text-xs font-semibold hover:bg-blue-700"
                      >
                        立即开票
                      </button>
                    ) : (inv.status === 'issued' || inv.status === 'issuing') && inv.invoiceNo ? (
                      <div className="flex items-center justify-end gap-2">
                        {inv.invoiceUrl ? (
                          <a href={inv.invoiceUrl} target="_blank" rel="noreferrer" className="text-blue-600 hover:text-blue-700 text-xs font-semibold flex items-center gap-1">
                            <Download className="w-3.5 h-3.5" /> 下载PDF
                          </a>
                        ) : <span className="text-xs text-gray-400">待关联附件</span>}
                        <label className="cursor-pointer text-xs font-semibold text-indigo-600 hover:text-indigo-700">
                          上传附件
                          <input type="file" accept="application/pdf,.pdf" className="hidden" onChange={(event) => void handleUploadInvoice(inv, event.target.files?.[0])} />
                        </label>
                      </div>
                    ) : (
                      <span className="text-xs text-gray-400">暂无可用操作</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
