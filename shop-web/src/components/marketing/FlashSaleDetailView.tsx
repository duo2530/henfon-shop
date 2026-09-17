import React, { useCallback, useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, Download, RefreshCw, Zap } from 'lucide-react';
import {
  BackendMarketingFlashSaleDetail,
  BackendMarketingFlashSaleItemRow,
  BackendMarketingFlashSaleReservationRow,
  BackendPage,
  getMarketingFlashSaleDetail,
  listMarketingFlashSaleItemRows,
  listMarketingFlashSaleReservations,
} from '../../api/adminApi';
import { useAdmin } from '../../context/AdminContext';
import { useExportCenter } from '../../context/ExportCenterContext';
import { formatDateTime, formatMinute } from '../../utils/datetime';

const ITEM_PAGE_SIZE = 10;
const RESERVATION_PAGE_SIZE = 10;

type ReservationStatusFilter = '' | 'reserved' | 'released';

/**
 * 活动状态标签配色，与列表页保持一致。
 *
 * @param statusText 服务端推导的状态文案
 */
function statusBadgeClass(statusText: string): string {
  if (statusText === '进行中') return 'bg-green-50 text-green-700 border-green-200';
  if (statusText === '即将开始') return 'bg-orange-50 text-orange-700 border-orange-200';
  if (statusText === '草稿') return 'bg-gray-100 text-gray-600 border-gray-200';
  return 'bg-red-50 text-red-700 border-red-200';
}

/** 表格分页控件，两张明细表共用同一套交互。 */
const Pagination: React.FC<{
  total: number;
  current: number;
  totalPages: number;
  onChange: (page: number) => void;
}> = ({ total, current, totalPages, onChange }) => (
  <div className="flex items-center justify-between text-xs text-gray-500 px-4 py-3 border-t border-[#E2E8F0]">
    <span>共 {total} 条，第 {Math.min(current, totalPages)} / {totalPages} 页</span>
    <span className="flex gap-2">
      <button type="button" onClick={() => onChange(Math.max(1, current - 1))} disabled={current <= 1}
        className="h-8 px-3 rounded-lg border border-gray-200 disabled:opacity-40">上一页</button>
      <button type="button" onClick={() => onChange(Math.min(totalPages, current + 1))} disabled={current >= totalPages}
        className="h-8 px-3 rounded-lg border border-gray-200 disabled:opacity-40">下一页</button>
    </span>
  </div>
);

/** 统计卡片，突出数值与口径说明。 */
const StatCard: React.FC<{ label: string; value: React.ReactNode; hint?: string }> = ({ label, value, hint }) => (
  <div className="bg-white rounded-xl border border-[#E2E8F0] p-4">
    <p className="text-xs text-gray-500">{label}</p>
    <p className="mt-1 text-xl font-bold text-[#191C1E] tabular-nums">{value}</p>
    {hint ? <p className="mt-1 text-[11px] text-gray-400">{hint}</p> : null}
  </div>
);

/**
 * 秒杀活动详情视图。
 *
 * <p>作为「秒杀与拼团」列表页的内嵌视图使用：活动由列表操作列的「详情」入口指定，
 * 不提供活动切换下拉，避免同一份数据在列表与详情两处各查一次。
 * 库存与预占统计全部来自服务端聚合，页面只负责展示，不在前端累加明细得出总量。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
export const FlashSaleDetailView: React.FC<{ activityId: number; onBack: () => void }> = ({ activityId, onBack }) => {
  const { showToast, hasPermission, requirePermission } = useAdmin();
  const { submit: submitExportTask } = useExportCenter();
  const canExport = hasPermission('marketing:flash:export');

  const [detail, setDetail] = useState<BackendMarketingFlashSaleDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [items, setItems] = useState<BackendPage<BackendMarketingFlashSaleItemRow> | null>(null);
  const [itemPage, setItemPage] = useState(1);
  const [itemLoading, setItemLoading] = useState(false);

  const [reservations, setReservations] = useState<BackendPage<BackendMarketingFlashSaleReservationRow> | null>(null);
  const [reservationPage, setReservationPage] = useState(1);
  const [reservationStatus, setReservationStatus] = useState<ReservationStatusFilter>('');
  const [reservationLoading, setReservationLoading] = useState(false);

  const loadDetail = useCallback(async (id: number) => {
    setLoading(true);
    setError(null);
    try {
      setDetail(await getMarketingFlashSaleDetail(id));
    } catch (requestError) {
      const message = requestError instanceof Error ? requestError.message : '秒杀活动详情加载失败，请稍后重试';
      setError(message);
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadDetail(activityId); }, [activityId, loadDetail]);

  // 切换活动时两张明细表都回到第一页，否则会带着上一活动的页码请求新活动数据。
  useEffect(() => {
    setItemPage(1);
    setReservationPage(1);
    setReservationStatus('');
  }, [activityId]);

  useEffect(() => {
    let cancelled = false;
    setItemLoading(true);
    void listMarketingFlashSaleItemRows(activityId, { current: itemPage, size: ITEM_PAGE_SIZE })
      .then((result) => { if (!cancelled) setItems(result); })
      .catch((requestError) => {
        if (cancelled) return;
        setItems(null);
        showToast(requestError instanceof Error ? requestError.message : '活动商品明细加载失败', 'error');
      })
      .finally(() => { if (!cancelled) setItemLoading(false); });
    return () => { cancelled = true; };
  }, [activityId, itemPage, showToast]);

  useEffect(() => {
    let cancelled = false;
    setReservationLoading(true);
    void listMarketingFlashSaleReservations(activityId, {
      current: reservationPage,
      size: RESERVATION_PAGE_SIZE,
      statusText: reservationStatus || undefined,
    })
      .then((result) => { if (!cancelled) setReservations(result); })
      .catch((requestError) => {
        if (cancelled) return;
        setReservations(null);
        showToast(requestError instanceof Error ? requestError.message : '预占记录加载失败', 'error');
      })
      .finally(() => { if (!cancelled) setReservationLoading(false); });
    return () => { cancelled = true; };
  }, [activityId, reservationPage, reservationStatus, showToast]);

  /** 切换预占状态筛选时回到第一页，避免停留在原页码但结果集已变化。 */
  const handleReservationStatusChange = (value: ReservationStatusFilter) => {
    setReservationStatus(value);
    setReservationPage(1);
  };

  const refreshAll = () => {
    void loadDetail(activityId);
    void listMarketingFlashSaleItemRows(activityId, { current: itemPage, size: ITEM_PAGE_SIZE })
      .then(setItems)
      .catch(() => showToast('活动商品明细刷新失败', 'error'));
    void listMarketingFlashSaleReservations(activityId, {
      current: reservationPage,
      size: RESERVATION_PAGE_SIZE,
      statusText: reservationStatus || undefined,
    })
      .then(setReservations)
      .catch(() => showToast('预占记录刷新失败', 'error'));
  };

  const handleExportItems = () => {
    if (!requirePermission('marketing:flash:export', '导出秒杀商品明细')) return;
    // 导出按活动收窄：后台任务会在执行时重新读取该活动的全量明细，不受当前页码影响。
    void submitExportTask({ exportType: 'FLASH_SALE_ITEM', activityId });
  };

  const handleExportReservations = () => {
    if (!requirePermission('marketing:flash:export', '导出秒杀预占记录')) return;
    void submitExportTask({
      exportType: 'FLASH_SALE_RESERVATION',
      activityId,
      statusText: reservationStatus || undefined,
    });
  };

  const itemTotalPages = items && items.size > 0 ? Math.max(1, Math.ceil(items.total / items.size)) : 1;
  const reservationTotalPages = reservations && reservations.size > 0
    ? Math.max(1, Math.ceil(reservations.total / reservations.size))
    : 1;

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4">
        <div className="flex items-start gap-3">
          <button type="button" onClick={onBack}
            className="mt-1 h-8 px-2.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-600 hover:bg-gray-50 inline-flex items-center gap-1 text-xs font-semibold">
            <ArrowLeft className="w-3.5 h-3.5" />返回列表
          </button>
          <div>
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E]">秒杀活动详情</h2>
            <p className="text-xs md:text-sm text-[#434655] mt-1">
              查看单个活动的库存消耗、会员参与情况与每一笔库存预占的去向。
            </p>
          </div>
        </div>
        <button type="button" onClick={refreshAll} disabled={loading}
          className="h-8 px-3 rounded-lg border border-gray-200 text-xs inline-flex items-center gap-1 disabled:opacity-40 self-start lg:self-auto">
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />刷新
        </button>
      </div>

      {error ? (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 flex items-center justify-between text-sm text-red-700">
          <span className="inline-flex items-center gap-2"><AlertCircle className="w-4 h-4" />{error}</span>
          <button type="button" onClick={() => void loadDetail(activityId)} className="text-xs font-semibold">重试</button>
        </div>
      ) : null}

      {loading && !detail ? <div role="status" className="py-14 text-center text-sm text-gray-400">活动详情加载中...</div> : null}

      {detail && (
        <>
          <div className="bg-white rounded-xl border border-[#E2E8F0] p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex gap-2 min-w-0">
                <span className="p-1.5 rounded-lg bg-orange-50 text-orange-600 shrink-0"><Zap className="w-4 h-4" /></span>
                <div className="min-w-0">
                  <h3 className="font-bold text-sm truncate">{detail.activityName}</h3>
                  <p className="font-mono text-[11px] text-gray-400 truncate">{detail.activityCode}</p>
                </div>
              </div>
              <span className={`text-[11px] font-semibold border px-2 py-0.5 rounded-full shrink-0 ${statusBadgeClass(detail.statusText)}`}>
                {detail.statusText}
              </span>
            </div>
            <dl className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
              <div><dt className="text-gray-500">活动时间</dt>
                <dd className="mt-0.5 text-gray-800">{formatMinute(detail.startAt)} 至 {formatMinute(detail.endAt)}</dd></div>
              <div><dt className="text-gray-500">单会员限购</dt><dd className="mt-0.5 text-gray-800">{detail.limitPerMember} 件</dd></div>
              <div><dt className="text-gray-500">创建时间</dt><dd className="mt-0.5 text-gray-800">{formatDateTime(detail.createdAt)}</dd></div>
              <div><dt className="text-gray-500">最近一次抢购</dt>
                <dd className="mt-0.5 text-gray-800">{formatDateTime(detail.latestReservedAt)}</dd></div>
            </dl>
          </div>

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <StatCard label="活动商品" value={`${detail.itemCount} 个`} />
            <StatCard label="活动总库存" value={detail.totalStock} />
            <StatCard label="已售 / 剩余" value={`${detail.soldStock} / ${detail.remainingStock}`}
              hint={`售罄率 ${detail.sellThroughRate}%`} />
            <StatCard label="参与会员" value={`${detail.participantCount} 人`}
              hint={`关联订单 ${detail.orderCount} 单`} />
            <StatCard label="预占记录" value={`${detail.reservationCount} 条`}
              hint={`预占中 ${detail.reservedQuantity} 件 / 已释放 ${detail.releasedQuantity} 件`} />
            <StatCard label="首次抢购时间" value={<span className="text-sm">{formatDateTime(detail.earliestReservedAt)}</span>} />
          </div>

          <section className="bg-white rounded-xl border border-[#E2E8F0] overflow-hidden">
            <header className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 border-b border-[#E2E8F0]">
              <div>
                <h3 className="text-sm font-semibold">活动商品明细</h3>
                <p className="text-[11px] text-gray-400 mt-0.5">原价取活动配置时的商品或规格价格，折扣率为活动价相对原价的比例。</p>
              </div>
              {canExport && (
                <button type="button" onClick={handleExportItems}
                  className="h-8 px-3 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5 text-xs font-semibold">
                  <Download className="w-3.5 h-3.5 text-gray-500" />导出商品明细
                </button>
              )}
            </header>
            {itemLoading ? (
              <div className="py-10 text-center text-sm text-gray-400">明细加载中...</div>
            ) : !items || items.records.length === 0 ? (
              <div className="py-10 text-center text-sm text-gray-400">该活动暂无商品明细。</div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead className="bg-gray-50 text-gray-500">
                      <tr>
                        <th className="text-left font-medium px-4 py-2">商品</th>
                        <th className="text-left font-medium px-4 py-2">规格</th>
                        <th className="text-right font-medium px-4 py-2">活动价</th>
                        <th className="text-right font-medium px-4 py-2">原价</th>
                        <th className="text-right font-medium px-4 py-2">折扣</th>
                        <th className="text-right font-medium px-4 py-2">库存</th>
                        <th className="text-right font-medium px-4 py-2">已售</th>
                        <th className="text-right font-medium px-4 py-2">剩余</th>
                        <th className="text-center font-medium px-4 py-2">状态</th>
                      </tr>
                    </thead>
                    <tbody>
                      {items.records.map((item) => (
                        <tr key={item.id} className="border-t border-[#E2E8F0]">
                          <td className="px-4 py-2.5">
                            <p className="text-gray-800">{item.productName || `商品 #${item.productId}`}</p>
                            <p className="font-mono text-[11px] text-gray-400">{item.productCode || '—'}</p>
                          </td>
                          <td className="px-4 py-2.5">
                            <p className="text-gray-800">{item.skuName || '商品整体（不指定 SKU）'}</p>
                            <p className="font-mono text-[11px] text-gray-400">{item.skuCode || '—'}</p>
                          </td>
                          <td className="px-4 py-2.5 text-right font-mono text-orange-600">¥{Number(item.activityPrice || 0).toFixed(2)}</td>
                          <td className="px-4 py-2.5 text-right font-mono text-gray-500">
                            {item.originalPrice == null ? '—' : `¥${Number(item.originalPrice).toFixed(2)}`}
                          </td>
                          <td className="px-4 py-2.5 text-right tabular-nums text-gray-700">
                            {item.discountRate == null ? '—' : `${item.discountRate}%`}
                          </td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{item.totalStock}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{item.soldStock}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{item.remainingStock}</td>
                          <td className="px-4 py-2.5 text-center">
                            <span className={`px-2 py-0.5 rounded-full text-[11px] ${item.status === 1 ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-600'}`}>
                              {item.statusText}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pagination total={items.total} current={itemPage} totalPages={itemTotalPages} onChange={setItemPage} />
              </>
            )}
          </section>

          <section className="bg-white rounded-xl border border-[#E2E8F0] overflow-hidden">
            <header className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 border-b border-[#E2E8F0]">
              <div>
                <h3 className="text-sm font-semibold">库存预占记录</h3>
                <p className="text-[11px] text-gray-400 mt-0.5">下单时预占活动库存，订单取消或超时会释放，释放时间留空表示占用仍在生效。</p>
              </div>
              <div className="flex items-end gap-2">
                <label className="flex flex-col gap-1 text-[11px] text-gray-500">
                  状态
                  <select value={reservationStatus} onChange={(event) => handleReservationStatusChange(event.target.value as ReservationStatusFilter)}
                    className="h-8 w-28 px-2 rounded-lg border border-gray-200 text-xs bg-white">
                    <option value="">全部</option>
                    <option value="reserved">预占中</option>
                    <option value="released">已释放</option>
                  </select>
                </label>
                {canExport && (
                  <button type="button" onClick={handleExportReservations}
                    className="h-8 px-3 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5 text-xs font-semibold">
                    <Download className="w-3.5 h-3.5 text-gray-500" />导出预占记录
                  </button>
                )}
              </div>
            </header>
            {reservationLoading ? (
              <div className="py-10 text-center text-sm text-gray-400">预占记录加载中...</div>
            ) : !reservations || reservations.records.length === 0 ? (
              <div className="py-10 text-center text-sm text-gray-400">暂无符合条件的预占记录。</div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead className="bg-gray-50 text-gray-500">
                      <tr>
                        <th className="text-left font-medium px-4 py-2">预占时间</th>
                        <th className="text-left font-medium px-4 py-2">会员</th>
                        <th className="text-left font-medium px-4 py-2">订单编号</th>
                        <th className="text-left font-medium px-4 py-2">商品 / 规格</th>
                        <th className="text-right font-medium px-4 py-2">数量</th>
                        <th className="text-center font-medium px-4 py-2">状态</th>
                        <th className="text-left font-medium px-4 py-2">释放时间</th>
                      </tr>
                    </thead>
                    <tbody>
                      {reservations.records.map((row) => (
                        <tr key={row.id} className="border-t border-[#E2E8F0]">
                          <td className="px-4 py-2.5 text-gray-700">{formatDateTime(row.createdAt)}</td>
                          <td className="px-4 py-2.5">
                            <p className="text-gray-800">{row.memberName || `会员 #${row.memberId}`}</p>
                            <p className="font-mono text-[11px] text-gray-400">{row.memberNo || row.memberPhone || '—'}</p>
                          </td>
                          <td className="px-4 py-2.5 font-mono text-gray-700">{row.orderNo || `订单 #${row.orderId}`}</td>
                          <td className="px-4 py-2.5">
                            <p className="text-gray-800">{row.productName || '—'}</p>
                            <p className="text-[11px] text-gray-400">{row.skuName || (row.skuId == null ? '商品整体' : `SKU #${row.skuId}`)}</p>
                          </td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{row.quantity}</td>
                          <td className="px-4 py-2.5 text-center">
                            <span className={`px-2 py-0.5 rounded-full text-[11px] ${row.status === 0 ? 'bg-orange-50 text-orange-700' : 'bg-gray-100 text-gray-600'}`}>
                              {row.statusText}
                            </span>
                          </td>
                          <td className="px-4 py-2.5 text-gray-500">{formatDateTime(row.releasedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pagination total={reservations.total} current={reservationPage} totalPages={reservationTotalPages}
                  onChange={setReservationPage} />
              </>
            )}
          </section>
        </>
      )}
    </div>
  );
};
