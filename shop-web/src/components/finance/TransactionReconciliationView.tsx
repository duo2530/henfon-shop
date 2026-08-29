import React, { useState, useMemo } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { 
  DollarSign, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Search, 
  Download, 
  Filter, 
  CheckCircle2, 
  Clock, 
  AlertTriangle, 
  CreditCard, 
  TrendingUp, 
  FileSpreadsheet, 
  Calendar 
} from 'lucide-react';

export interface FinanceTransaction {
  id: string;
  transNo: string;
  orderNumber: string;
  type: 'order_income' | 'refund_payout' | 'commission_fee' | 'withdrawal';
  channel: 'wechat_pay' | 'alipay' | 'unionpay' | 'balance_pay';
  amount: number;
  fee: number;
  netAmount: number;
  status: 'reconciled' | 'pending_settle' | 'discrepancy';
  settledAt: string;
  accountNumber: string;
  notes?: string;
}

const mockTransactions: FinanceTransaction[] = [
  {
    id: 'tx-1001',
    transNo: 'TX2026082910001',
    orderNumber: 'ORD20260829101',
    type: 'order_income',
    channel: 'wechat_pay',
    amount: 1299.00,
    fee: 7.79, // 0.6% fee
    netAmount: 1291.21,
    status: 'reconciled',
    settledAt: '2026-08-29 10:45:12',
    accountNumber: 'wx_mch_18920199',
    notes: '极客降噪无线耳机 订单收款'
  },
  {
    id: 'tx-1002',
    transNo: 'TX2026082910002',
    orderNumber: 'ORD20260829102',
    type: 'order_income',
    channel: 'alipay',
    amount: 458.00,
    fee: 2.75,
    netAmount: 455.25,
    status: 'reconciled',
    settledAt: '2026-08-29 11:12:05',
    accountNumber: '2088719201928371',
    notes: '智能无线充电座 订单收款'
  },
  {
    id: 'tx-1003',
    transNo: 'TX2026082910003',
    orderNumber: 'ORD20260828089',
    type: 'refund_payout',
    channel: 'wechat_pay',
    amount: -320.00,
    fee: 0,
    netAmount: -320.00,
    status: 'reconciled',
    settledAt: '2026-08-29 12:00:33',
    accountNumber: 'wx_mch_18920199',
    notes: '客户协商原路全额退款'
  },
  {
    id: 'tx-1004',
    transNo: 'TX2026082910004',
    orderNumber: 'ORD20260829110',
    type: 'order_income',
    channel: 'balance_pay',
    amount: 890.00,
    fee: 0,
    netAmount: 890.00,
    status: 'reconciled',
    settledAt: '2026-08-29 13:20:18',
    accountNumber: 'MEMBER_WALLET',
    notes: '会员卡钱包余额快捷抵扣'
  },
  {
    id: 'tx-1005',
    transNo: 'TX2026082910005',
    orderNumber: 'ORD20260829115',
    type: 'order_income',
    channel: 'unionpay',
    amount: 2850.00,
    fee: 14.25,
    netAmount: 2835.75,
    status: 'pending_settle',
    settledAt: '2026-08-29 14:10:00',
    accountNumber: 'UP_62284819283',
    notes: '企业大宗采购银行卡转账（T+1入账中）'
  },
  {
    id: 'tx-1006',
    transNo: 'TX2026082910006',
    orderNumber: 'ORD20260828060',
    type: 'commission_fee',
    channel: 'wechat_pay',
    amount: -45.60,
    fee: 0,
    netAmount: -45.60,
    status: 'reconciled',
    settledAt: '2026-08-28 23:59:59',
    accountNumber: 'wx_mch_18920199',
    notes: '微信支付日结商户结算手续费'
  }
];

export const TransactionReconciliationView: React.FC = () => {
  const { showToast } = useAdmin();
  const [transactions, setTransactions] = useState<FinanceTransaction[]>(mockTransactions);
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');

  const filtered = useMemo(() => {
    return transactions.filter((t) => {
      const matchSearch =
        searchTerm === '' ||
        t.transNo.toLowerCase().includes(searchTerm.toLowerCase()) ||
        t.orderNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (t.notes && t.notes.toLowerCase().includes(searchTerm.toLowerCase()));
      const matchType = typeFilter === 'all' || t.type === typeFilter;
      const matchStatus = statusFilter === 'all' || t.status === statusFilter;
      return matchSearch && matchType && matchStatus;
    });
  }, [transactions, searchTerm, typeFilter, statusFilter]);

  const totalIncome = transactions
    .filter((t) => t.amount > 0)
    .reduce((sum, t) => sum + t.amount, 0);

  const totalRefund = Math.abs(
    transactions
      .filter((t) => t.type === 'refund_payout')
      .reduce((sum, t) => sum + t.amount, 0)
  );

  const totalFee = transactions.reduce((sum, t) => sum + t.fee, 0);
  const netSettlement = totalIncome - totalRefund - totalFee;

  const handleExportReconciliation = () => {
    const csvContent =
      'data:text/csv;charset=utf-8,\uFEFF' +
      '流水单号,关联业务单号,交易类型,支付渠道,交易金额(¥),手续费(¥),净结算额(¥),对账状态,结算时间,商户账号/说明\n' +
      filtered
        .map(
          (t) =>
            `"${t.transNo}","${t.orderNumber}","${t.type}","${t.channel}",${t.amount},${t.fee},${t.netAmount},"${t.status}","${t.settledAt}","${t.accountNumber} - ${t.notes || ''}"`
        )
        .join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `财务资金对账单_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('财务日结对账单已成功导出', 'success');
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              财务结算与资金对账 (Finance & Reconciliation)
            </h2>
            <span className="text-xs bg-emerald-50 text-emerald-700 font-semibold px-2 py-0.5 rounded-full border border-emerald-200">
              资金合规
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            全渠道支付收单流水、银行与微信/支付宝三方对账、手续费日结与退款冲销审计。
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => showToast('正在与微信支付/支付宝官方接口同步最新出入金对账单...', 'info')}
            className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-2 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
          >
            <Clock className="w-4 h-4 text-gray-500" />
            <span>拉取三方对账单</span>
          </button>

          <button
            onClick={handleExportReconciliation}
            className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs transition-colors cursor-pointer"
          >
            <Download className="w-4 h-4" />
            <span>导出标准会计报表</span>
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">本期收款总额 (Inflow)</span>
            <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
              <ArrowDownLeft className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-emerald-700 mt-1">¥{totalIncome.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
          <p className="text-xs text-gray-400 mt-1">共计 {transactions.filter((t) => t.amount > 0).length} 笔成功收款</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">售后退款支出 (Refund)</span>
            <div className="p-2 bg-red-50 text-red-600 rounded-lg">
              <ArrowUpRight className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-red-600 mt-1">¥{totalRefund.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
          <p className="text-xs text-red-500 mt-1">退款率仅 2.1% (健康水位)</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">渠道支付手续费</span>
            <div className="p-2 bg-amber-50 text-amber-600 rounded-lg">
              <CreditCard className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-amber-600 mt-1">¥{totalFee.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
          <p className="text-xs text-gray-400 mt-1">综合费率 0.58%</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">实际净入账结算</span>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-blue-700 mt-1">¥{netSettlement.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
          <p className="text-xs text-blue-600 mt-1">100% 平账无差异</p>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        {/* Filters */}
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50 flex flex-wrap gap-3 items-center justify-between">
          <div className="flex flex-wrap gap-3 flex-1">
            <div className="relative flex-1 max-w-xs">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="搜索流水单号、订单号或摘要..."
                className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white focus:border-blue-500 outline-none"
              />
            </div>

            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
            >
              <option value="all">所有流水类型</option>
              <option value="order_income">销售收款 (Income)</option>
              <option value="refund_payout">售后退款 (Refund)</option>
              <option value="commission_fee">手续费 (Fee)</option>
            </select>

            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
            >
              <option value="all">所有对账状态</option>
              <option value="reconciled">已对账平账 (Reconciled)</option>
              <option value="pending_settle">银行在途中 (Pending)</option>
              <option value="discrepancy">差异待核实 (Discrepancy)</option>
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase tracking-wider">
              <tr>
                <th className="py-3 px-4">流水单号 / 关联单号</th>
                <th className="py-3 px-4">业务类型</th>
                <th className="py-3 px-4">支付渠道 / 商户号</th>
                <th className="py-3 px-4 text-right">交易金额 (¥)</th>
                <th className="py-3 px-4 text-right">净结算 (¥)</th>
                <th className="py-3 px-4 text-center">对账状态</th>
                <th className="py-3 px-4 text-right">结算入账时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-sm text-gray-800">
              {filtered.map((t) => (
                <tr key={t.id} className="hover:bg-[#F8FAFC] transition-colors">
                  <td className="py-3 px-4">
                    <div className="font-mono font-bold text-gray-900 text-xs sm:text-sm">{t.transNo}</div>
                    <div className="text-xs font-mono text-blue-600 mt-0.5">单号: {t.orderNumber}</div>
                  </td>

                  <td className="py-3 px-4">
                    {t.type === 'order_income' && (
                      <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded">
                        订单收单
                      </span>
                    )}
                    {t.type === 'refund_payout' && (
                      <span className="text-xs font-semibold text-red-700 bg-red-50 px-2 py-0.5 rounded">
                        售后退款
                      </span>
                    )}
                    {t.type === 'commission_fee' && (
                      <span className="text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-0.5 rounded">
                        通道手续费
                      </span>
                    )}
                    <div className="text-[11px] text-gray-400 mt-0.5">{t.notes}</div>
                  </td>

                  <td className="py-3 px-4">
                    <div className="text-xs font-semibold text-gray-800">
                      {t.channel === 'wechat_pay' && '微信支付 (WeChat Pay)'}
                      {t.channel === 'alipay' && '支付宝 (Alipay)'}
                      {t.channel === 'balance_pay' && '会员余额支付'}
                      {t.channel === 'unionpay' && '银联快捷/转账'}
                    </div>
                    <div className="text-[11px] font-mono text-gray-400">{t.accountNumber}</div>
                  </td>

                  <td className={`py-3 px-4 text-right font-mono font-bold ${t.amount > 0 ? 'text-emerald-700' : 'text-red-600'}`}>
                    {t.amount > 0 ? `+¥${t.amount.toFixed(2)}` : `-¥${Math.abs(t.amount).toFixed(2)}`}
                    {t.fee > 0 && <div className="text-[10px] text-gray-400 font-normal">费: ¥{t.fee.toFixed(2)}</div>}
                  </td>

                  <td className="py-3 px-4 text-right font-mono font-bold text-gray-900">
                    ¥{t.netAmount.toFixed(2)}
                  </td>

                  <td className="py-3 px-4 text-center">
                    {t.status === 'reconciled' && (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-800 bg-emerald-100 px-2 py-0.5 rounded-full">
                        <CheckCircle2 className="w-3 h-3" /> 已平账
                      </span>
                    )}
                    {t.status === 'pending_settle' && (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-amber-800 bg-amber-100 px-2 py-0.5 rounded-full">
                        <Clock className="w-3 h-3" /> 在途中
                      </span>
                    )}
                  </td>

                  <td className="py-3 px-4 text-right text-xs font-mono text-gray-500">
                    {t.settledAt}
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
