import React, { useCallback, useEffect, useState, useMemo } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { listPaymentReconciliation, actionPaymentReconciliation, BackendPaymentReconciliationRecord } from '../../api/adminApi';
import { 
  DollarSign, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Search, 
  Download, 
  CheckCircle2, 
  Clock, 
  AlertCircle,
  CreditCard, 
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

function toFinanceTransaction(record: BackendPaymentReconciliationRecord): FinanceTransaction {
  return {
    id: record.id,
    transNo: record.transNo,
    orderNumber: record.orderNumber,
    type: record.type,
    channel: record.channel,
    amount: Number(record.amount || 0),
    fee: Number(record.fee || 0),
    netAmount: Number(record.netAmount || 0),
    status: record.status,
    settledAt: record.settledAt || '-',
    accountNumber: record.accountNumber || '-',
    notes: record.notes,
  };
}

export const TransactionReconciliationView: React.FC = () => {
  const { showToast } = useAdmin();
  const [transactions, setTransactions] = useState<FinanceTransaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');

  const loadTransactions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listPaymentReconciliation({
        current: 1,
        size: 200,
        keyword: searchTerm.trim() || undefined,
        type: typeFilter,
        status: statusFilter,
      });
      setTransactions((page.records || []).map(toFinanceTransaction));
    } catch (err) {
      setTransactions([]);
      setError(err instanceof Error ? err.message : '财务流水加载失败');
    } finally {
      setLoading(false);
    }
  }, [searchTerm, showToast, statusFilter, typeFilter]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadTransactions(), 250);
    return () => window.clearTimeout(timer);
  }, [loadTransactions]);

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
    .filter((t) => t.type === 'order_income' && t.amount > 0)
    .reduce((sum, t) => sum + t.amount, 0);

  const totalRefund = Math.abs(
    transactions
      .filter((t) => t.type === 'refund_payout')
      .reduce((sum, t) => sum + t.amount, 0)
  );

  const totalFee = transactions.reduce((sum, t) => sum + t.fee, 0);
  const netSettlement = totalIncome - totalRefund - totalFee;
  const incomeCount = transactions.filter((t) => t.type === 'order_income' && t.amount > 0).length;
  const refundRate = totalIncome > 0 ? (totalRefund / totalIncome) * 100 : 0;
  const feeRate = totalIncome > 0 ? (totalFee / totalIncome) * 100 : 0;
  const reconciledCount = transactions.filter((t) => t.status === 'reconciled').length;
  const reconciledRate = transactions.length > 0 ? (reconciledCount / transactions.length) * 100 : 0;
  const formatRate = (value: number) => `${value.toFixed(2)}%`;

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

  const handleReconciliationAction = async (transaction: FinanceTransaction) => {
    const action = window.prompt('请输入处理动作：confirm=确认平账，ignore=忽略，remark=仅备注，rematch=重新匹配退款');
    if (!action) return;
    const remark = window.prompt('处理备注（可选）') || undefined;
    const matchPaymentNo = action.trim().toLowerCase() === 'rematch'
      ? window.prompt('请输入目标支付单号') || undefined
      : undefined;
    try {
      await actionPaymentReconciliation(transaction.id, { action: action.trim().toLowerCase() as 'confirm' | 'ignore' | 'remark' | 'rematch', remark, matchPaymentNo });
      showToast('对账处理已保存', 'success');
      await loadTransactions();
    } catch (err) {
      showToast(err instanceof Error ? err.message : '对账处理失败', 'error');
    }
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
            微信支付收单流水、银行对账、手续费日结与退款冲销审计；其他支付渠道暂未接入。
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => { void loadTransactions(); }}
            className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-2 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
          >
            <Clock className="w-4 h-4 text-gray-500" />
            <span>{loading ? '正在加载流水...' : '刷新真实流水'}</span>
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
          <p className="text-xs text-gray-400 mt-1">共计 {incomeCount} 笔收款流水</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">售后退款支出 (Refund)</span>
            <div className="p-2 bg-red-50 text-red-600 rounded-lg">
              <ArrowUpRight className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-red-600 mt-1">¥{totalRefund.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
          <p className="text-xs text-red-500 mt-1">当前流水退款率 {formatRate(refundRate)}</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">渠道支付手续费</span>
            <div className="p-2 bg-amber-50 text-amber-600 rounded-lg">
              <CreditCard className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-amber-600 mt-1">¥{totalFee.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
          <p className="text-xs text-gray-400 mt-1">当前流水综合费率 {formatRate(feeRate)}</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">实际净入账结算</span>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-blue-700 mt-1">¥{netSettlement.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
          <p className="text-xs text-blue-600 mt-1">{formatRate(reconciledRate)} 流水已平账（共 {transactions.length} 笔）</p>
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
              <option value="withdrawal">提现 (Withdrawal)</option>
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
          {error && (
            <div className="m-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 flex items-center justify-between gap-3">
              <span>{error}</span>
              <button type="button" onClick={() => { void loadTransactions(); }} className="font-semibold underline">重新加载</button>
            </div>
          )}
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
                <th className="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-sm text-gray-800">
              {loading ? (
                <tr><td colSpan={7} className="py-12 text-center text-gray-500">正在加载真实财务流水...</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={7} className="py-12 text-center text-gray-500">暂无符合条件的财务流水</td></tr>
              ) : filtered.map((t) => (
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
                      {t.channel === 'alipay' && '其他渠道（历史）'}
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
                    {t.status === 'discrepancy' && (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-red-800 bg-red-100 px-2 py-0.5 rounded-full">
                        <AlertCircle className="w-3 h-3" /> 待核实
                      </span>
                    )}
                  </td>

                  <td className="py-3 px-4 text-right">
                    {t.status === 'discrepancy' && (
                      <button onClick={() => void handleReconciliationAction(t)} className="text-xs text-blue-600 hover:text-blue-800">处理</button>
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
