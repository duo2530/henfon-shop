import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { 
  FileText, 
  Search, 
  Download, 
  CheckCircle, 
  Clock, 
  X, 
  Eye, 
  Printer, 
  Send, 
  Building, 
  User, 
  AlertCircle 
} from 'lucide-react';

export interface InvoiceRecord {
  id: string;
  invoiceNo: string;
  orderNumber: string;
  type: 'special_vat' | 'general_vat' | 'electronic';
  title: string;
  taxCode: string;
  amount: number;
  taxRate: number;
  taxAmount: number;
  applicantEmail: string;
  status: 'issued' | 'pending' | 'rejected' | 'red_ink';
  createdAt: string;
  issuedAt?: string;
}

const mockInvoices: InvoiceRecord[] = [
  {
    id: 'inv-001',
    invoiceNo: 'INV20260829001',
    orderNumber: 'ORD20260829101',
    type: 'special_vat',
    title: '上海极客无限网络技术有限公司',
    taxCode: '91310115MA1K39482X',
    amount: 1299.00,
    taxRate: 13,
    taxAmount: 149.44,
    applicantEmail: 'finance@geeknetwork.com',
    status: 'issued',
    createdAt: '2026-08-29 11:00:00',
    issuedAt: '2026-08-29 11:30:22'
  },
  {
    id: 'inv-002',
    invoiceNo: 'INV20260829002',
    orderNumber: 'ORD20260829105',
    type: 'electronic',
    title: '个人 (李伟)',
    taxCode: '-',
    amount: 389.00,
    taxRate: 13,
    taxAmount: 44.75,
    applicantEmail: 'liwei882@qq.com',
    status: 'issued',
    createdAt: '2026-08-29 13:20:15',
    issuedAt: '2026-08-29 13:25:00'
  },
  {
    id: 'inv-003',
    invoiceNo: 'INV20260829003',
    orderNumber: 'ORD20260829118',
    type: 'special_vat',
    title: '杭州西湖数码创新工作室',
    taxCode: '91330106MA27K8391A',
    amount: 2850.00,
    taxRate: 13,
    taxAmount: 327.88,
    applicantEmail: 'tax@hzwestlake.cn',
    status: 'pending',
    createdAt: '2026-08-29 14:40:00'
  }
];

export const InvoiceManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [invoices, setInvoices] = useState<InvoiceRecord[]>(mockInvoices);
  const [searchTerm, setSearchTerm] = useState('');
  const [inspectInvoice, setInspectInvoice] = useState<InvoiceRecord | null>(null);

  const handleIssueInvoice = (id: string) => {
    setInvoices((prev) =>
      prev.map((inv) => {
        if (inv.id === id) {
          return {
            ...inv,
            status: 'issued',
            issuedAt: new Date().toISOString().replace('T', ' ').substring(0, 19)
          };
        }
        return inv;
      })
    );
    showToast('电子发票已完成航信金税盘直连开具并推送到客户邮箱', 'success');
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
          onClick={() => showToast('已批量将今日审核通过的发票推送到税控盘', 'info')}
          className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs"
        >
          <Send className="w-4 h-4" />
          <span>批量金税直连开具</span>
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

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase">
              <tr>
                <th className="py-3 px-4">发票号码 / 订单号</th>
                <th className="py-3 px-4">发票类型</th>
                <th className="py-3 px-4">抬头与纳税人识别号</th>
                <th className="py-3 px-4 text-right">开票金额 (¥)</th>
                <th className="py-3 px-4 text-right">税额 (13%)</th>
                <th className="py-3 px-4 text-center">状态</th>
                <th className="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-sm">
              {invoices.map((inv) => (
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
                    ¥{inv.taxAmount.toFixed(2)}
                  </td>

                  <td className="py-3 px-4 text-center">
                    {inv.status === 'issued' ? (
                      <span className="text-xs font-semibold text-emerald-800 bg-emerald-100 px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                        <CheckCircle className="w-3 h-3" /> 已开具
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
                        onClick={() => handleIssueInvoice(inv.id)}
                        className="px-2.5 py-1 bg-blue-600 text-white rounded text-xs font-semibold hover:bg-blue-700"
                      >
                        立即开票
                      </button>
                    ) : (
                      <button
                        onClick={() => showToast(`已下载发票 PDF: ${inv.invoiceNo}.pdf`, 'success')}
                        className="text-blue-600 hover:text-blue-700 text-xs font-semibold flex items-center gap-1 ml-auto"
                      >
                        <Download className="w-3.5 h-3.5" /> 下载PDF
                      </button>
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
